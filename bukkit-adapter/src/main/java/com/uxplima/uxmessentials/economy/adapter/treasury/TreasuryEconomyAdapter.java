package com.uxplima.uxmessentials.economy.adapter.treasury;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.adapter.outbound.BoundedAwait;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.response.EconomySubscriber;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionInitiator;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges a foreign Treasury economy into this plugin's {@link EconomyProvider} port, the primary bridge
 * when Treasury is on the server ({@code docs/11-economy-integration.md} §2, §6). This is one of only two
 * classes (with {@code VaultEconomyAdapter}) allowed to import a provider SDK; the ArchUnit fence
 * {@code economyDomainHasNoProviderSdk} forbids {@code me.lokka30.treasury..} anywhere above this adapter.
 *
 * <p>Treasury's API is asynchronous/subscriber-based; each call is bridged to the synchronous port through
 * {@link EconomySubscriber#asFuture} and joined. Every port call already runs off the tick thread (the
 * command layer's {@code Scheduler.async} + {@code orTimeout} guard, §4.3), so the join never blocks a
 * region thread. Treasury currency identifiers are mapped to this plugin's {@link Currency} at this boundary
 * via {@link TreasuryCurrencies}; nothing above the adapter sees a Treasury type. A currency the configured
 * registry does not know is reported as {@code CURRENCY_UNSUPPORTED} rather than silently converted.
 */
@NullMarked
public final class TreasuryEconomyAdapter implements EconomyProvider {

    private final me.lokka30.treasury.api.economy.EconomyProvider treasury;
    private final TreasuryCurrencies currencies;
    private final MessageKey unsupportedCurrency;

    public TreasuryEconomyAdapter(
            me.lokka30.treasury.api.economy.EconomyProvider treasury,
            CurrencyRegistry registry,
            MessageKey unsupportedCurrency) {
        this.treasury = Objects.requireNonNull(treasury, "treasury");
        this.currencies = new TreasuryCurrencies(treasury, Objects.requireNonNull(registry, "registry"));
        this.unsupportedCurrency = Objects.requireNonNull(unsupportedCurrency, "unsupportedCurrency");
    }

    @Override
    public boolean hasAccount(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(currency, "currency");
        return await(sub -> treasury.hasPlayerAccount(owner.uuid(), sub));
    }

    @Override
    public void ensureAccount(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        if (!hasAccount(owner, currency)) {
            TreasuryEconomyAdapter.<PlayerAccount>await(sub -> treasury.createPlayerAccount(owner.uuid(), sub));
        }
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        return currencies
                .toTreasury(currency)
                .map(tc -> Money.of(
                        currency,
                        account(owner)
                                .map(a -> read(sub -> a.retrieveBalance(tc, sub)))
                                .orElse(BigDecimal.ZERO)))
                .orElse(Money.zero(currency));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        return currencies
                .toTreasury(amount.currency())
                .<Result<Unit, TransferError>>map(tc -> {
                    PlayerAccount account = retrieveOrCreate(owner);
                    read(sub -> account.depositBalance(amount.amount(), initiator(owner), tc, sub));
                    return Result.ok();
                })
                .orElse(Result.err(TransferError.CURRENCY_UNSUPPORTED));
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        return currencies
                .toTreasury(amount.currency())
                .<Result<Unit, TransferError>>map(tc -> {
                    PlayerAccount account = retrieveOrCreate(owner);
                    if (read(sub -> account.retrieveBalance(tc, sub)).compareTo(amount.amount()) < 0) {
                        return Result.err(TransferError.INSUFFICIENT_FUNDS);
                    }
                    read(sub -> account.withdrawBalance(amount.amount(), initiator(owner), tc, sub));
                    return Result.ok();
                })
                .orElse(Result.err(TransferError.CURRENCY_UNSUPPORTED));
    }

    @Override
    public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            return TransferResult.denyWith(unsupportedCurrency);
        }
        if (currencies.toTreasury(amount.currency()).isEmpty()) {
            return TransferResult.denyWith(unsupportedCurrency);
        }
        Result<Unit, TransferError> debited = debit(from, amount);
        if (debited.isErr()) {
            return TransferResult.insufficientFunds(amount, balance(from, amount.currency()));
        }
        credit(to, amount);
        return TransferResult.allow(
                com.uxplima.uxmessentials.economy.domain.Transaction.debit(
                        from, amount, balance(from, amount.currency()), java.time.Instant.now()),
                com.uxplima.uxmessentials.economy.domain.Transaction.credit(
                        to, amount, balance(to, amount.currency()), java.time.Instant.now()));
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        // Treasury exposes no ranked read-model; baltop over a foreign Treasury economy reads the account
        // ids and balances and ranks here. Bounded by the caller's limit after sorting descending.
        return currencies
                .toTreasury(currency)
                .map(tc -> rank(currency, tc, limit))
                .orElse(List.of());
    }

    @Override
    public Set<Currency> currencies() {
        return currencies.served();
    }

    private List<BaltopRow> rank(Currency currency, me.lokka30.treasury.api.economy.currency.Currency tc, int limit) {
        java.util.Collection<java.util.UUID> ids = await(treasury::retrievePlayerAccountIds);
        List<BaltopRow> rows = new ArrayList<>();
        for (java.util.UUID id : ids) {
            PlayerRef owner = new PlayerRef(id, id.toString());
            BigDecimal balance = account(owner)
                    .map(a -> read(sub -> a.retrieveBalance(tc, sub)))
                    .orElse(BigDecimal.ZERO);
            rows.add(new BaltopRow(owner, Money.of(currency, balance)));
        }
        rows.sort(java.util.Comparator.comparing((BaltopRow r) -> r.balance().amount())
                .reversed());
        return rows.size() <= limit ? List.copyOf(rows) : List.copyOf(rows.subList(0, limit));
    }

    private PlayerAccount retrieveOrCreate(PlayerRef owner) {
        return account(owner).orElseGet(() -> await(sub -> treasury.createPlayerAccount(owner.uuid(), sub)));
    }

    private java.util.Optional<PlayerAccount> account(PlayerRef owner) {
        boolean present = TreasuryEconomyAdapter.<Boolean>await(sub -> treasury.hasPlayerAccount(owner.uuid(), sub));
        if (!present) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(
                TreasuryEconomyAdapter.<PlayerAccount>await(sub -> treasury.retrievePlayerAccount(owner.uuid(), sub)));
    }

    private static EconomyTransactionInitiator<?> initiator(PlayerRef owner) {
        return EconomyTransactionInitiator.createInitiator(EconomyTransactionInitiator.Type.PLAYER, owner.uuid());
    }

    /** Bound the wait on the foreign Treasury provider so a stalled backend can never hang the caller. */
    private static final Duration TREASURY_AWAIT_TIMEOUT = Duration.ofSeconds(10);

    private static <T> T await(java.util.function.Consumer<EconomySubscriber<T>> call) {
        CompletableFuture<T> future = EconomySubscriber.asFuture(call);
        return BoundedAwait.get(future, TREASURY_AWAIT_TIMEOUT, "Treasury economy provider");
    }

    private static BigDecimal read(java.util.function.Consumer<EconomySubscriber<BigDecimal>> call) {
        BigDecimal value = await(call);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** The configured ids served by both the registry and the foreign Treasury provider. */
    Set<Currency> servedCurrencies() {
        return new LinkedHashSet<>(currencies.served());
    }
}
