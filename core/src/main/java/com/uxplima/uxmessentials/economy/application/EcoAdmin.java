package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The eco-admin surface, {@code /eco give|take|set|reset} per target and {@code /eco giveall|giverandom|resetall}
 * in bulk. Every verb is permission-gated at the command boundary and audited exactly once here (bulk verbs
 * with an affected-count, never per-target spam, {@code docs/11-economy-integration.md} §9.4 to §9.5). The
 * single-sided {@code give}/{@code take} route through the {@link EconomyProvider} so they honour the
 * currency clamp and the offline-target upsert; {@code set}/{@code reset} target an exact balance, which only
 * the native ledger expresses, so they go through the {@link WalletRepository} the economy context owns.
 *
 * <p>An offline target is first materialised via {@link WalletRepository#ensureOwner}, so a give/set to a
 * never-joined player never breaks a foreign key (the {@code ensureUserExists} invariant, §10.2).
 */
public final class EcoAdmin {

    private final EconomyProvider economy;
    private final WalletRepository repository;
    private final EconomyAudit audit;
    private final EconomyNotifier notifier;
    private final TransactionHistory history;
    private final Clock clock;

    public EcoAdmin(
            EconomyProvider economy,
            WalletRepository repository,
            EconomyAudit audit,
            EconomyNotifier notifier,
            TransactionHistory history,
            Clock clock) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.history = Objects.requireNonNull(history, "history");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@code /eco give <target> <amount> [currency]}: credit {@code target} by {@code amount}. */
    public Result<Unit, TransferError> give(PlayerRef actor, PlayerRef target, Money amount) {
        return single(actor, target, amount, true, EconomyReason.ADMIN_GIVE, EconomyMessageKey.ECO_ADMIN_GIVEN);
    }

    /** {@code /eco take <target> <amount> [currency]}: debit {@code target} by {@code amount}. */
    public Result<Unit, TransferError> take(PlayerRef actor, PlayerRef target, Money amount) {
        return single(actor, target, amount, false, EconomyReason.ADMIN_TAKE, EconomyMessageKey.ECO_ADMIN_TAKEN);
    }

    /** {@code /eco set <target> <amount> [currency]}: set {@code target}'s balance to exactly {@code amount}. */
    public Result<Unit, TransferError> set(PlayerRef actor, PlayerRef target, Money amount) {
        validate(actor, target, amount);
        repository.ensureOwner(target);
        Money before = economy.balance(target, amount.currency());
        repository.upsertBalance(target, amount);
        recordNetChange(target, before, amount, EconomyReason.ADMIN_SET);
        audit.adminMutation(actor, target, amount, EconomyReason.ADMIN_SET);
        notify(actor, target, amount, EconomyMessageKey.ECO_ADMIN_SET);
        return Result.ok();
    }

    /** {@code /eco reset <target> [currency]}: set {@code target}'s balance to the currency's starting figure. */
    public Result<Unit, TransferError> reset(PlayerRef actor, PlayerRef target, Currency currency) {
        Money starting = Money.of(currency, currency.starting());
        validate(actor, target, starting);
        repository.ensureOwner(target);
        Money before = economy.balance(target, currency);
        repository.upsertBalance(target, starting);
        recordNetChange(target, before, starting, EconomyReason.ADMIN_RESET);
        audit.adminMutation(actor, target, starting, EconomyReason.ADMIN_RESET);
        notify(actor, target, starting, EconomyMessageKey.ECO_ADMIN_RESET);
        return Result.ok();
    }

    /** {@code /eco giveall <amount> [currency]}: credit every wallet in {@code targets} by {@code amount}. */
    public int giveAll(PlayerRef actor, List<PlayerRef> targets, Money amount) {
        validateBulk(actor, amount);
        int affected = creditEach(targets, amount, EconomyReason.ADMIN_GIVEALL);
        audit.bulkMutation(actor, amount, affected, EconomyReason.ADMIN_GIVEALL);
        notifier.send(
                actor,
                EconomyMessageKey.ECO_ADMIN_GIVEALL,
                Map.of("count", Integer.toString(affected), "amount", notifier.amount(amount)));
        return affected;
    }

    /** {@code /eco giverandom <amount> [currency]}: credit {@code amount} to one chosen online player. */
    public Result<Unit, TransferError> giveRandom(PlayerRef actor, PlayerRef chosen, Money amount) {
        validate(actor, chosen, amount);
        repository.ensureOwner(chosen);
        Result<Unit, TransferError> result = economy.credit(chosen, amount);
        if (result.isErr()) {
            return result;
        }
        history.recordCredit(ownerId(chosen), amount, EconomyReason.ADMIN_GIVERANDOM, clock.millis());
        audit.bulkMutation(actor, amount, 1, EconomyReason.ADMIN_GIVERANDOM);
        notifier.send(
                actor,
                EconomyMessageKey.ECO_ADMIN_GIVERANDOM,
                Map.of("player", chosen.name(), "amount", notifier.amount(amount)));
        return Result.ok();
    }

    /** {@code /eco give-random <target> <min> <max> [currency]}: credit {@code target} by a random amount in range {@code [min, max]}. */
    public Result<Money, TransferError> giveRandomRange(PlayerRef actor, PlayerRef target, Money min, Money max) {
        validate(actor, target, min);
        Objects.requireNonNull(max, "max");
        if (!min.currency().equals(max.currency())) {
            throw new IllegalArgumentException("currencies must match");
        }
        if (min.amount().compareTo(max.amount()) > 0) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }

        Money amount = randomInRange(min, max);

        repository.ensureOwner(target);
        Result<Unit, TransferError> result = economy.credit(target, amount);
        if (result.isErr()) {
            return Result.err(result.errorOrThrow());
        }
        history.recordCredit(ownerId(target), amount, EconomyReason.ADMIN_GIVE, clock.millis());
        audit.adminMutation(actor, target, amount, EconomyReason.ADMIN_GIVE);
        notifier.send(
                actor,
                EconomyMessageKey.ECO_ADMIN_GIVEN,
                Map.of("player", target.name(), "amount", notifier.amount(amount)));
        return Result.ok(amount);
    }

    /** {@code /eco resetall [currency] --confirm}: reset every wallet in {@code targets} to {@code starting}. */
    public int resetAll(PlayerRef actor, List<PlayerRef> targets, Currency currency) {
        Money starting = Money.of(currency, currency.starting());
        validateBulk(actor, starting);
        int affected = 0;
        for (PlayerRef target : List.copyOf(targets)) {
            repository.ensureOwner(target);
            Money before = economy.balance(target, currency);
            repository.upsertBalance(target, starting);
            recordNetChange(target, before, starting, EconomyReason.ADMIN_RESETALL);
            affected++;
        }
        audit.bulkMutation(actor, starting, affected, EconomyReason.ADMIN_RESETALL);
        notifier.send(actor, EconomyMessageKey.ECO_ADMIN_RESETALL, Map.of("count", Integer.toString(affected)));
        return affected;
    }

    /**
     * A random amount in {@code [min, max]}, computed in {@link java.math.BigDecimal} so no money figure ever
     * passes through a {@code double}. The only floating-point value is the {@code [0,1)} fraction the RNG
     * yields; it scales the exact decimal range, and the result is normalised to the currency's precision.
     */
    private static Money randomInRange(Money min, Money max) {
        Currency currency = min.currency();
        java.math.BigDecimal range = max.amount().subtract(min.amount());
        double fraction = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        java.math.BigDecimal offset = range.multiply(java.math.BigDecimal.valueOf(fraction));
        java.math.BigDecimal value =
                min.amount().add(offset).setScale(currency.precision(), java.math.RoundingMode.HALF_UP);
        return Money.of(currency, value);
    }

    private Result<Unit, TransferError> single(
            PlayerRef actor,
            PlayerRef target,
            Money amount,
            boolean credit,
            EconomyReason reason,
            EconomyMessageKey feedback) {
        validate(actor, target, amount);
        repository.ensureOwner(target);
        Result<Unit, TransferError> result = credit ? economy.credit(target, amount) : economy.debit(target, amount);
        if (result.isErr()) {
            notifier.send(actor, result.errorOrThrow().messageKey(), Map.of("amount", notifier.amount(amount)));
            return result;
        }
        if (credit) {
            history.recordCredit(ownerId(target), amount, reason, clock.millis());
        } else {
            history.recordDebit(ownerId(target), amount, reason, clock.millis());
        }
        audit.adminMutation(actor, target, amount, reason);
        notify(actor, target, amount, feedback);
        return Result.ok();
    }

    private static String ownerId(PlayerRef owner) {
        return owner.uuid().toString();
    }

    /**
     * Records the net change of an exact-balance write (set/reset) as a single credit or debit. A no-op write
     * (the new balance already matches the old) records nothing, so a zero-delta set never logs a phantom row.
     */
    private void recordNetChange(PlayerRef target, Money before, Money after, EconomyReason reason) {
        int sign = after.amount().compareTo(before.amount());
        if (sign == 0) {
            return;
        }
        Money delta = Money.of(
                after.currency(), after.amount().subtract(before.amount()).abs());
        if (sign > 0) {
            history.recordCredit(ownerId(target), delta, reason, clock.millis());
        } else {
            history.recordDebit(ownerId(target), delta, reason, clock.millis());
        }
    }

    private int creditEach(List<PlayerRef> targets, Money amount, EconomyReason reason) {
        int affected = 0;
        for (PlayerRef target : List.copyOf(targets)) {
            repository.ensureOwner(target);
            if (economy.credit(target, amount).isOk()) {
                history.recordCredit(ownerId(target), amount, reason, clock.millis());
                affected++;
            }
        }
        return affected;
    }

    private void notify(PlayerRef actor, PlayerRef target, Money amount, EconomyMessageKey feedback) {
        notifier.send(actor, feedback, Map.of("player", target.name(), "amount", notifier.amount(amount)));
    }

    private void validate(PlayerRef actor, PlayerRef target, Money amount) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");
    }

    private void validateBulk(PlayerRef actor, Money amount) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(amount, "amount");
    }
}
