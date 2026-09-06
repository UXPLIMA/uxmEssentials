package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The {@link EconomyProvider} every caller sees. It owns no money: it reads a {@link Currency}'s
 * {@code backendId} and hands the work to that {@link CurrencyBackend}. A currency on the native ledger keeps
 * exactly the guarantees it had before this class existed. The guarded {@code UPDATE}, the atomic two-sided
 * transfer, the ranked baltop.
 *
 * <p>Every configured currency's backend is resolved once, in the constructor. A currency naming a backend the
 * server does not have is a startup failure, never a quiet fall-back to the native ledger: paying a warp fee
 * out of the wrong economy is worse than refusing to boot.
 *
 * <p>A transfer whose two legs cannot commit together (any currency not on the native ledger) debits, then
 * credits, and on a failed credit issues a compensating credit back to the payer. That compensation is
 * best-effort; if it also fails the payer is short and the failure is logged at error, which is the honest
 * outcome for an economy that offers no transaction.
 */
public final class RoutingEconomyProvider implements EconomyProvider {

    private final CurrencyBackendRegistry backends;
    private final CurrencyRegistry currencies;
    private final Clock clock;
    private final Logger log;

    public RoutingEconomyProvider(
            CurrencyBackendRegistry backends, CurrencyRegistry currencies, Clock clock, Logger log) {
        this.backends = Objects.requireNonNull(backends, "backends");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
        for (Currency currency : currencies.all()) {
            if (backends.find(currency.backendId()).isEmpty()) {
                throw new IllegalStateException("currency " + currency.id().value()
                        + " names unknown backend '" + currency.backendId()
                        + "'; known backends: " + backends.ids());
            }
        }
    }

    private CurrencyBackend backendFor(Currency currency) {
        return backends.find(currency.backendId())
                .orElseThrow(() -> new IllegalStateException(
                        "no backend for currency " + currency.id().value()));
    }

    @Override
    public boolean hasAccount(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        return !backendFor(currency).balance(owner, currency).isZero();
    }

    @Override
    public void ensureAccount(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        // Only the native ledger has rows to materialise; a foreign economy opens an account on first write.
        if (backendFor(currency) instanceof NativeCurrencyBackend nativeBackend) {
            nativeBackend.ensureOwner(owner);
        }
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        return backendFor(currency).balance(owner, currency);
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return backendFor(amount.currency()).credit(owner, amount);
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return backendFor(amount.currency()).debit(owner, amount);
    }

    @Override
    public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        if (from.equals(to)) {
            return TransferResult.denyWith(EconomyMessageKey.PAY_SELF);
        }
        CurrencyBackend backend = backendFor(amount.currency());
        if (backend instanceof NativeCurrencyBackend nativeBackend) {
            return nativeTransfer(nativeBackend, from, to, amount);
        }
        return compensatingTransfer(backend, from, to, amount);
    }

    private TransferResult nativeTransfer(NativeCurrencyBackend backend, PlayerRef from, PlayerRef to, Money amount) {
        Result<Unit, TransferError> moved = backend.transferAtomically(from, to, amount);
        if (moved.isErr()) {
            // A shortfall reads back the payer's live balance for the "you have X" message; the recipient being
            // at max-balance is its own failure and is surfaced by its own key rather than mislabelled as the
            // payer having too little.
            TransferError error = moved.errorOrThrow();
            return error == TransferError.INSUFFICIENT_FUNDS
                    ? TransferResult.insufficientFunds(amount, backend.balance(from, amount.currency()))
                    : TransferResult.denyWith(error.messageKey());
        }
        return allow(backend, from, to, amount);
    }

    private TransferResult compensatingTransfer(CurrencyBackend backend, PlayerRef from, PlayerRef to, Money amount) {
        Result<Unit, TransferError> debited = backend.debit(from, amount);
        if (debited.isErr()) {
            return TransferResult.insufficientFunds(amount, backend.balance(from, amount.currency()));
        }
        Result<Unit, TransferError> credited = backend.credit(to, amount);
        if (credited.isErr()) {
            compensate(backend, from, amount);
            return TransferResult.insufficientFunds(amount, backend.balance(from, amount.currency()));
        }
        return allow(backend, from, to, amount);
    }

    private void compensate(CurrencyBackend backend, PlayerRef from, Money amount) {
        Result<Unit, TransferError> restored = backend.credit(from, amount);
        if (restored.isErr()) {
            log.error(
                    "event=pay_compensation_failed player=" + from.uuid() + " name=" + from.name()
                            + " currency=" + amount.currency().id().value()
                            + " amount=" + amount.amount() + " backend=" + backend.id(),
                    new IllegalStateException("compensating credit rejected: " + restored.errorOrThrow()));
        }
    }

    private TransferResult allow(CurrencyBackend backend, PlayerRef from, PlayerRef to, Money amount) {
        Money fromAfter = backend.balance(from, amount.currency());
        Money toAfter = backend.balance(to, amount.currency());
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
        return backendFor(currency).top(currency, limit);
    }

    @Override
    public Set<Currency> currencies() {
        return currencies.all();
    }
}
