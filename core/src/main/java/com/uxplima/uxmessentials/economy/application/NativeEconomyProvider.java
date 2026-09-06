package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The native ledger's {@link EconomyProvider}. The direct DB-backed implementation a native currency runs
 * on. It holds <strong>no money state of its own</strong>:
 * every read is a {@link WalletRepository} read and every write is a guarded transaction the repository owns,
 * so invariant (d) (balances are DB-backed, never PDC) is honoured at the source and the double-spend
 * guard is the repository's guarded {@code UPDATE}, not a JVM lock ({@code docs/11-economy-integration.md}
 * §2.1, §3). This class is pure application code: it never imports a Vault or Treasury type (the ArchUnit
 * fence) and translates only between the port vocabulary and the repository.
 *
 * <p>The plugin no longer registers this directly: {@link RoutingEconomyProvider} is the provider callers
 * see, delegating a native currency here through the {@code CurrencyBackend} seam.
 */
public final class NativeEconomyProvider implements EconomyProvider {

    private final WalletRepository repo;
    private final CurrencyRegistry currencies;
    private final Clock clock;

    public NativeEconomyProvider(WalletRepository repo, CurrencyRegistry currencies, Clock clock) {
        this.repo = Objects.requireNonNull(repo, "repo");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean hasAccount(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(currency, "currency");
        return repo.findByOwner(Objects.requireNonNull(owner, "owner"))
                .map(wallet -> !wallet.balanceOf(currency).isZero())
                .orElse(false);
    }

    @Override
    public void ensureAccount(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        repo.ensureOwner(owner);
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(currency, "currency");
        return repo.findByOwner(Objects.requireNonNull(owner, "owner"))
                .map(wallet -> wallet.balanceOf(currency))
                .orElse(Money.zero(currency));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        repo.ensureOwner(owner);
        return repo.credit(owner, amount);
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return repo.debit(owner, amount);
    }

    @Override
    public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        if (from.equals(to)) {
            return TransferResult.denyWith(EconomyMessageKey.PAY_SELF);
        }
        repo.ensureOwner(to);
        Result<Unit, TransferError> moved = repo.transfer(from, to, amount);
        if (moved.isErr()) {
            // INSUFFICIENT_FUNDS reads back the payer's live balance for the "you have X" message; any other
            // guarded failure (the target at max-balance) is surfaced by its own key rather than mislabelled
            // as a shortfall on the payer's side.
            TransferError error = moved.errorOrThrow();
            return error == TransferError.INSUFFICIENT_FUNDS
                    ? TransferResult.insufficientFunds(amount, balance(from, amount.currency()))
                    : TransferResult.denyWith(error.messageKey());
        }
        Money fromAfter = balance(from, amount.currency());
        Money toAfter = balance(to, amount.currency());
        return TransferResult.allow(
                Transaction.debit(from, amount, fromAfter, clock.instant()),
                Transaction.credit(to, amount, toAfter, clock.instant()));
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        if (limit <= 0) {
            throw new IllegalArgumentException("baltop limit must be positive: " + limit);
        }
        return repo.top(currency, limit);
    }

    @Override
    public Set<Currency> currencies() {
        return currencies.all();
    }
}
