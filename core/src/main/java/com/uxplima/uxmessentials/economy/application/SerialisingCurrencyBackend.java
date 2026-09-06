package com.uxplima.uxmessentials.economy.application;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Serialises debits per {@code (owner, currency)} for a backend that cannot offer a guarded compare-and-take.
 * A foreign economy exposes "read the balance" and "take some" as two separate calls, so two threads can both
 * observe the pre-debit balance and both succeed. This decorator closes that window inside one JVM.
 *
 * <p>It closes nothing across a cluster: two servers debiting the same foreign economy hold different locks and
 * cannot be serialised by us at all. That is why {@link #atomicDebit()} keeps reporting {@code false} through
 * the wrapper. Callers must keep knowing the promise is JVM-local, so that config validation can refuse to
 * schedule recurring charges against such a currency unless the operator opts in.
 *
 * <p>Only {@link #debit(PlayerRef, Money)} is serialised. Credits and reads run unguarded: a lost credit cannot
 * overdraw an account, and serialising reads would turn a menu render into a queue behind whatever debit holds
 * the key. The lock is held around the delegate's {@code debit} call and nothing else. That single I/O call is
 * the whole reason the class exists, so it is deliberately the one thing inside the lock.
 */
public final class SerialisingCurrencyBackend implements CurrencyBackend {

    private final CurrencyBackend delegate;

    // One small ReentrantLock per (owner, currency) ever debited, kept for the JVM's life. The map is bounded by
    // the roster of players who have joined times the handful of foreign currencies. Thousands of tiny entries,
    // not an unbounded leak, and eviction is unsafe anyway: the lock only serialises when every contender shares
    // the same instance, so removing one would need reference counting on the hot path that costs more than the
    // few megabytes it would reclaim.
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private SerialisingCurrencyBackend(CurrencyBackend delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** {@code backend} unchanged when it debits atomically; otherwise the same backend behind a keyed lock. */
    public static CurrencyBackend wrapIfNeeded(CurrencyBackend backend) {
        Objects.requireNonNull(backend, "backend");
        return backend.atomicDebit() ? backend : new SerialisingCurrencyBackend(backend);
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        ReentrantLock lock = locks.computeIfAbsent(
                owner.uuid() + "|" + amount.currency().id().value(), key -> new ReentrantLock());
        lock.lock();
        try {
            return delegate.debit(owner, amount);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public boolean available() {
        return delegate.available();
    }

    @Override
    public boolean worksOffline() {
        return delegate.worksOffline();
    }

    @Override
    public boolean atomicDebit() {
        return false;
    }

    @Override
    public Precision precision() {
        return delegate.precision();
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        return delegate.balance(owner, currency);
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return delegate.credit(owner, amount);
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        return delegate.top(currency, limit);
    }
}
