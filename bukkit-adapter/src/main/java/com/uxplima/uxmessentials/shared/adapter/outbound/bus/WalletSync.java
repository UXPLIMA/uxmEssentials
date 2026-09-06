package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.persistence.economy.CachedWalletRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The economy context's cross-server sync seam: the second representative wiring alongside {@link HomeSync}.
 * It wraps the cached {@link WalletRepository} so every local balance mutation publishes a
 * {@link BalanceChanged} frame after the guarded transaction commits, and returns a {@link RemoteSyncListener}
 * that drops the affected owner from the same cache on a remote change.
 *
 * <p>The frame is a notification, not the figure: the cross-server transfer's debit/credit is one atomic
 * transaction against the shared row ({@code docs/02-concurrency.md} §6.7), and the bus only invalidates the
 * peer's read cache so it never serves a stale balance. The {@code upsertBalance} coalesced-settle path also
 * announces, so a flushed credit reaches peers. The guarded debit/credit/transfer themselves are unchanged
 * the decorator forwards to the same repository; it only appends the announcement.
 */
@NullMarked
public final class WalletSync {

    private WalletSync() {}

    /** A {@link WalletRepository} that broadcasts a {@link BalanceChanged} after every local balance write. */
    public static WalletRepository repository(WalletRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /** A listener that invalidates the affected owner's cached wallet on a remote {@link BalanceChanged}. */
    public static RemoteSyncListener listener(CachedWalletRepository cache) {
        Objects.requireNonNull(cache, "cache");
        return message -> {
            if (message instanceof BalanceChanged changed) {
                cache.invalidateOwner(changed.owner());
            }
        };
    }

    /** Forwards every call to the cached delegate, announcing each balance mutation on the bus. */
    private static final class Broadcasting implements WalletRepository {

        private final WalletRepository delegate;
        private final BusPublisher bus;

        Broadcasting(WalletRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            return delegate.findByOwner(owner);
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return delegate.ensureOwner(owner);
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            delegate.upsertBalance(owner, balance);
            announce(owner, balance.currency());
        }

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            Result<Unit, TransferError> result = delegate.transfer(from, to, amount);
            if (result.isOk()) {
                announce(from, amount.currency());
                announce(to, amount.currency());
            }
            return result;
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            Result<Unit, TransferError> result = delegate.debit(owner, amount);
            if (result.isOk()) {
                announce(owner, amount.currency());
            }
            return result;
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            Result<Unit, TransferError> result = delegate.credit(owner, amount);
            if (result.isOk()) {
                announce(owner, amount.currency());
            }
            return result;
        }

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
            Result<Unit, TransferError> result = delegate.exchange(owner, debit, credit);
            if (result.isOk()) {
                announce(owner, debit.currency());
                announce(owner, credit.currency());
            }
            return result;
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return delegate.top(currency, limit);
        }

        private void announce(PlayerRef owner, Currency currency) {
            bus.publish(new BalanceChanged(
                    bus.serverId(), owner.uuid(), currency.id().value()));
        }
    }
}
