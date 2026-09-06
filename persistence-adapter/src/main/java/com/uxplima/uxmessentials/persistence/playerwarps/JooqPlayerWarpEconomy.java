package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The player-warps {@link PlayerWarpEconomy} seam over the resolved {@link EconomyProvider}: it charges a
 * visitor the warp's entry price, banks the owner's share on the {@code player_warps.earned_amount} column, and
 * pays that bank out to the owner on withdraw. It lives in the persistence adapter, not the {@code :core}
 * player-warps context. Precisely because it is the one place allowed to see both the economy types and the
 * warp bank column; the narrow port keeps the domain free of any economy import (the {@code WarpEconomy}
 * precedent, {@code docs/11-economy-integration.md} §4.2).
 *
 * <h2>The cut is deflationary, never a {@code TaxSink}</h2>
 * The payer is debited the full {@code price}; the bank accrues only {@code price − cut}. So the server's cut is
 * exactly the gap between what left the payer's wallet and what will ever be credited to the owner, money that
 * is never minted anywhere and therefore leaves circulation on its own. Accruing {@code net = price − cut}
 * <em>is</em> the cut; there is no separate sink credit. Routing the cut to a named holding account instead of
 * deflating it is a future config option, deliberately out of scope here.
 *
 * <h2>debit-then-accrue-then-compensate</h2>
 * A single transaction spanning the wallet table and the warp bank would mean reaching across the economy port
 * into its storage: a layering violation, and impossible for a foreign backend that offers no transaction. So
 * the charge is uniform for every backend: the {@link EconomyProvider#debit(PlayerRef, Money) debit} is the
 * DB-guarded, double-spend-safe point (two concurrent uses can never both pass it past zero), and only once it
 * takes does the bank accrue. If the accrue then throws, a compensating {@link EconomyProvider#credit credit}
 * makes the payer whole and the charge reports {@link ChargeError#ACCRUAL_FAILED}; if that compensation also
 * fails the payer is genuinely short, which is logged at error as an operator-visible money discrepancy (never a
 * secret): the honest outcome, never swallowed. There is no check-then-charge: the debit either takes in full
 * or reports {@link ChargeError#INSUFFICIENT_FUNDS}, so no affordability probe opens a double-spend window.
 *
 * <h2>Withdraw is a guarded read-then-zero</h2>
 * {@link #withdraw} reads the bank, zeroes it with a guard ({@code SET earned_amount = 0 WHERE id = ? AND
 * earned_amount = <read>}), and only then credits the owner. The guard makes the read-then-zero safe under
 * concurrency: a second withdraw that read the same amount matches no rows once the first has committed, so it
 * pays nothing rather than double-crediting. A credit that fails after the zero committed re-accrues the amount
 * so the money returns to the bank rather than vanishing.
 */
@NullMarked
public final class JooqPlayerWarpEconomy extends JooqRepository implements PlayerWarpEconomy {

    private final EconomyProvider economy;
    private final CurrencyRegistry currencies;
    private final CurrencyBackendRegistry backends;
    private final PayoutConfig config;
    private final Logger log;

    public JooqPlayerWarpEconomy(
            Persistence persistence,
            EconomyProvider economy,
            CurrencyRegistry currencies,
            CurrencyBackendRegistry backends,
            PayoutConfig config,
            Logger log) {
        super(persistence.dsl());
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.backends = Objects.requireNonNull(backends, "backends");
        this.config = Objects.requireNonNull(config, "config");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Result<Unit, ChargeError> chargeAndAccrue(
            PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currencyId, "currencyId");
        Currency currency = resolve(currencyId);
        Money charge = Money.of(currency, price);
        Result<Unit, TransferError> debited = economy.debit(payer, charge);
        if (debited.isErr()) {
            return Result.err(chargeErrorFor(debited.errorOrThrow()));
        }
        return accrueThenPayout(payer, warp, currency, charge);
    }

    /**
     * The debit has taken; bank {@code net} to the owner and, if configured and safe, immediately pay it out. An
     * accrue that throws unwinds the debit with a compensating credit rather than leaving the payer short.
     */
    private Result<Unit, ChargeError> accrueThenPayout(
            PlayerRef payer, PlayerWarpId warp, Currency currency, Money charge) {
        BigDecimal net = netOf(currency, charge.amount());
        int accrued;
        try {
            accrued = bumpBank(warp, net, currency.id().value());
        } catch (RuntimeException accrualFailure) {
            return compensate(payer, warp, charge, accrualFailure);
        }
        if (accrued == 0) {
            // The warp row vanished between the debit and this accrue, a concurrent /pwarp delete or admin purge
            // in the off-tick window (holding the aggregate in memory does not lock the DB row). The increment hit
            // no rows, so nothing banked; refund the payer exactly as a thrown accrue would rather than debiting
            // them with nothing to show for it.
            return compensate(
                    payer,
                    warp,
                    charge,
                    new IllegalStateException("accrue affected 0 rows: warp " + warp.value() + " no longer exists"));
        }
        autoPayout(warp, currency);
        return Result.ok();
    }

    @Override
    public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(to, "to");
        Optional<BankSnapshot> found = snapshot(warp);
        if (found.isEmpty() || found.get().amount().signum() <= 0) {
            return Result.ok();
        }
        BankSnapshot bank = found.get();
        Currency currency = resolve(bank.currencyId());
        // The guarded zero is the concurrency point: only the withdraw whose read still matches wins, so a
        // concurrent second withdraw updates zero rows and pays nothing rather than double-crediting the owner.
        if (zeroBank(warp, bank.amount()) == 0) {
            return Result.ok();
        }
        Money payout = Money.of(currency, bank.amount());
        Result<Unit, TransferError> credited = economy.credit(to, payout);
        return credited.isErr() ? failedWithdraw(warp, to, payout, credited.errorOrThrow()) : Result.ok();
    }

    @Override
    public Result<Unit, ChargeError> collectRent(
            PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        Currency currency = resolve(currencyId);
        BigDecimal due = currency.normalize(amount);
        if (due.signum() <= 0) {
            return Result.ok();
        }
        // Spend the warp's own bank first (a warp that earns pays its own rent), then the owner's wallet for the
        // shortfall. The debit is the DB-guarded, double-spend-safe point; there is no check-then-charge.
        BigDecimal fromBank = spendBank(warp, currency, due);
        BigDecimal shortfall = currency.normalize(due.subtract(fromBank));
        if (shortfall.signum() <= 0) {
            return Result.ok();
        }
        Result<Unit, TransferError> debited = economy.debit(owner, Money.of(currency, shortfall));
        if (debited.isOk()) {
            return Result.ok();
        }
        // The wallet could not cover the shortfall: unwind the bank deduction so the bank is never left short
        // without the rent actually being collected, then report why the charge could not take.
        refundBank(warp, currency, fromBank);
        return Result.err(chargeErrorFor(debited.errorOrThrow()));
    }

    /**
     * Deduct up to {@code due} from the warp bank as one guarded UPDATE and return how much it covered. The bank is
     * only spent when it currently holds the same currency the rent is charged in; a bank denominated in another
     * currency is left untouched and the whole rent falls to the wallet. The deduction is guarded on the exact read
     * value, so a bank changed concurrently matches no row and contributes nothing rather than double-spending.
     */
    private BigDecimal spendBank(PlayerWarpId warp, Currency currency, BigDecimal due) {
        Optional<BankSnapshot> found = snapshot(warp);
        if (found.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BankSnapshot bank = found.get();
        if (bank.amount().signum() <= 0 || !resolve(bank.currencyId()).id().equals(currency.id())) {
            return BigDecimal.ZERO;
        }
        BigDecimal use = bank.amount().min(due);
        return deductBankGuarded(warp, bank.amount(), use) == 0 ? BigDecimal.ZERO : use;
    }

    /** Subtract {@code delta} from the bank only if it still holds exactly {@code expected}; the row count says who won. */
    private int deductBankGuarded(PlayerWarpId warp, BigDecimal expected, BigDecimal delta) {
        return write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.EARNED_AMOUNT, PLAYER_WARPS.EARNED_AMOUNT.sub(delta))
                .where(PLAYER_WARPS.ID.eq(warp.value()))
                .and(PLAYER_WARPS.EARNED_AMOUNT.eq(expected))
                .execute());
    }

    /** Put a spent-but-uncollected bank deduction back; a re-accrue that hits no row is logged, never thrown. */
    private void refundBank(PlayerWarpId warp, Currency currency, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return;
        }
        reAccrue(warp, Money.of(currency, amount));
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        Currency currency = resolve(currencyId);
        return !economy.balance(who, currency).isLessThan(Money.of(currency, amount));
    }

    @Override
    public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        Currency currency = resolve(currencyId);
        return economy.credit(to, Money.of(currency, amount)).mapErr(JooqPlayerWarpEconomy::chargeErrorFor);
    }

    @Override
    public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        Currency currency = resolve(currencyId);
        BigDecimal due = currency.normalize(amount);
        if (due.signum() <= 0) {
            return Result.ok();
        }
        // A plain guarded owner debit with nowhere to accrue. The sponsorship fee leaves circulation, exactly like
        // the entry-fee cut and the rent shortfall. The debit is the DB-guarded, double-spend-safe point, so there is
        // no check-then-charge: it either takes in full or reports INSUFFICIENT_FUNDS.
        return economy.debit(owner, Money.of(currency, due)).mapErr(JooqPlayerWarpEconomy::chargeErrorFor);
    }

    /**
     * {@code net = price − cut}, where {@code cut = price × cutPercent / 100}, each figure scaled to the
     * currency's precision so the banked amount never carries stray digits. The cut is not credited anywhere
     * it is the deflationary gap between the debit and the eventual owner credit (see the class note).
     */
    private BigDecimal netOf(Currency currency, BigDecimal price) {
        BigDecimal cut = currency.normalize(price.multiply(config.cutPercent()).movePointLeft(2));
        return currency.normalize(price.subtract(cut));
    }

    /**
     * Accrue {@code delta} onto the warp bank as one guarded PK update, recording the currency it now holds, and
     * return the affected-row count. A {@code 0} means the warp row is gone (a concurrent delete/purge) so nothing
     * was banked: callers treat that as a failed accrue, not a silent success.
     */
    private int bumpBank(PlayerWarpId warp, BigDecimal delta, String currencyId) {
        return write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.EARNED_AMOUNT, PLAYER_WARPS.EARNED_AMOUNT.add(delta))
                .set(PLAYER_WARPS.EARNED_CURRENCY, currencyId)
                .where(PLAYER_WARPS.ID.eq(warp.value()))
                .execute());
    }

    /** Zero the bank only if it still holds exactly {@code expected}; the row count says whether this call won. */
    private int zeroBank(PlayerWarpId warp, BigDecimal expected) {
        return write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.EARNED_AMOUNT, BigDecimal.ZERO)
                .where(PLAYER_WARPS.ID.eq(warp.value()))
                .and(PLAYER_WARPS.EARNED_AMOUNT.eq(expected))
                .execute());
    }

    /** The debit could not be banked; refund the payer and, if even that fails, log the discrepancy honestly. */
    private Result<Unit, ChargeError> compensate(
            PlayerRef payer, PlayerWarpId warp, Money charge, RuntimeException cause) {
        Result<Unit, TransferError> restored = economy.credit(payer, charge);
        if (restored.isErr()) {
            log.error(
                    "event=playerwarp_charge_discrepancy warp=" + warp.value() + " payer=" + payer.uuid() + " name="
                            + payer.name() + " currency="
                            + charge.currency().id().value() + " amount="
                            + charge.amount(),
                    new IllegalStateException(
                            "compensating credit rejected after accrual failure: " + restored.errorOrThrow(), cause));
        }
        return Result.err(ChargeError.ACCRUAL_FAILED);
    }

    /** The bank was zeroed but the owner credit was refused; put the money back and report a provider fault. */
    private Result<Unit, ChargeError> failedWithdraw(
            PlayerWarpId warp, PlayerRef to, Money payout, TransferError error) {
        reAccrue(warp, payout);
        log.error(
                "event=playerwarp_withdraw_discrepancy warp=" + warp.value() + " owner=" + to.uuid() + " name="
                        + to.name() + " currency=" + payout.currency().id().value() + " amount=" + payout.amount(),
                new IllegalStateException("withdraw credit rejected after bank zeroed: " + error));
        return Result.err(ChargeError.PROVIDER_ERROR);
    }

    /** Best-effort return of a failed payout to the bank; a re-accrue that itself throws is logged, not thrown. */
    private void reAccrue(PlayerWarpId warp, Money payout) {
        try {
            if (bumpBank(warp, payout.amount(), payout.currency().id().value()) == 0) {
                // The warp was deleted after its bank had been zeroed, so the money cannot go back onto a row that
                // no longer exists: it is genuinely lost from the bank. Surface it as an operator-visible
                // discrepancy rather than discarding the row count and losing it silently.
                log.error(
                        "event=playerwarp_reaccrue_lost warp=" + warp.value() + " currency="
                                + payout.currency().id().value() + " amount=" + payout.amount(),
                        new IllegalStateException("re-accrue affected 0 rows: warp no longer exists"));
            }
        } catch (RuntimeException reAccrualFailure) {
            log.error(
                    "event=playerwarp_reaccrue_failed warp=" + warp.value() + " currency="
                            + payout.currency().id().value() + " amount=" + payout.amount(),
                    reAccrualFailure);
        }
    }

    /**
     * Immediately settle the just-accrued bank to the owner when auto-payout is on and the currency can be
     * credited to an offline owner. An XP/placeholder currency that cannot be written offline is left in the bank
     * for a manual {@code /pwarp withdraw}; the charge itself succeeds regardless of the payout outcome.
     */
    private void autoPayout(PlayerWarpId warp, Currency currency) {
        if (!config.autoPayout() || !worksOffline(currency)) {
            return;
        }
        try {
            ownerOf(warp).ifPresent(owner -> withdraw(warp, owner));
        } catch (RuntimeException payoutFailure) {
            // The debit and accrue have already committed, so the owner's share is safely banked and a manual
            // /pwarp withdraw settles it. A fault in this convenience payout (e.g. the snapshot read throwing) must
            // never turn a correctly-banked charge into a thrown call. Log it (no secret; the money is safe) and
            // leave the amount in the bank. The charge stays ok either way, which is the contract.
            log.warn(
                    "event=playerwarp_auto_payout_failed warp={} currency={} reason={}",
                    warp.value(),
                    currency.id().value(),
                    payoutFailure.toString());
        }
    }

    private boolean worksOffline(Currency currency) {
        return backends.find(currency.backendId())
                .map(CurrencyBackend::worksOffline)
                .orElse(false);
    }

    /**
     * Resolve the currency for a bare id, treating the {@code "default"} sentinel and any unknown id as the
     * default currency (mirrors {@code ProviderWarpEconomy.resolve}); never constructs a {@code CurrencyId} so a
     * malformed stored id can never throw here.
     */
    private Currency resolve(String currencyId) {
        if (currencyId.equalsIgnoreCase("default")) {
            return currencies.defaultCurrency();
        }
        return currencies.all().stream()
                .filter(currency -> currency.id().value().equalsIgnoreCase(currencyId))
                .findFirst()
                .orElse(currencies.defaultCurrency());
    }

    private Optional<PlayerRef> ownerOf(PlayerWarpId warp) {
        return snapshot(warp).map(BankSnapshot::owner);
    }

    private Optional<BankSnapshot> snapshot(PlayerWarpId warp) {
        return read(dsl -> dsl.select(
                        PLAYER_WARPS.OWNER,
                        PLAYER_WARPS.OWNER_NAME,
                        PLAYER_WARPS.EARNED_AMOUNT,
                        PLAYER_WARPS.EARNED_CURRENCY)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(warp.value()))
                .fetchOptional()
                .map(row -> new BankSnapshot(playerRef(row.value1(), row.value2()), row.value3(), row.value4())));
    }

    private static PlayerRef playerRef(String uuid, @Nullable String name) {
        UUID id = UUID.fromString(uuid);
        return new PlayerRef(id, name != null ? name : uuid);
    }

    private static ChargeError chargeErrorFor(TransferError error) {
        return error == TransferError.INSUFFICIENT_FUNDS ? ChargeError.INSUFFICIENT_FUNDS : ChargeError.PROVIDER_ERROR;
    }

    /** The owner and current bank of a warp, read in one select for the withdraw and auto-payout paths. */
    private record BankSnapshot(PlayerRef owner, BigDecimal amount, String currencyId) {}

    /**
     * The owner-cut percentage and auto-payout switch this bridge reads from {@code modules/playerwarps/config.conf}
     * ({@code payout.cut-percent} / {@code payout.auto-payout}). Kept as a {@link BigDecimal} so the cut arithmetic
     * never touches a {@code double}.
     *
     * @param cutPercent the server's share of each entry fee, as a percentage (e.g. {@code 10} for 10%)
     * @param autoPayout whether an entry fee is settled to the owner immediately rather than banked for withdraw
     */
    public record PayoutConfig(BigDecimal cutPercent, boolean autoPayout) {

        private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

        public PayoutConfig {
            Objects.requireNonNull(cutPercent, "cutPercent");
            // Clamp, never reject: a typo'd percent must not crash module enable. The invariant that matters is that
            // net = price − cut stays in [0, price]; a cut above 100 would drive net negative (adding a negative
            // delta to the bank) and a cut below 0 would overpay it, so pin the percent to [0, 100]. The wiring
            // read site log.warns when it had to clamp; the record stays a pure value with no logger.
            cutPercent = cutPercent.max(BigDecimal.ZERO).min(MAX_PERCENT);
        }
    }
}
