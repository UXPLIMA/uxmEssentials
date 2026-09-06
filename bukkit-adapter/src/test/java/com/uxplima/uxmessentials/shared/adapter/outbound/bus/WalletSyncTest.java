package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import org.junit.jupiter.api.Test;

/**
 * Pins the surviving cross-server wallet invalidation path now that the bespoke {@code RedisWalletSync}
 * side-channel is gone: a remote {@link BalanceChanged} frame routed to {@link WalletSync#listener} must drop
 * exactly the affected owner from the {@link CachedWalletRepository}, so the next {@code /balance} on this
 * backend reloads the authoritative figure from the shared database. A frame of another type must leave the
 * cache untouched. This invalidation used to be covered only through the deleted Redis broadcaster's test.
 */
class WalletSyncTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");

    @Test
    void aRemoteBalanceChangedDropsTheOwnerSoTheNextReadReloads() {
        CountingDelegate delegate = new CountingDelegate();
        CachedWalletRepository cached = new CachedWalletRepository(delegate);

        cached.findByOwner(OWNER); // prime the cache from the delegate
        cached.findByOwner(OWNER); // served from cache, no extra delegate read
        assertThat(delegate.reads.get()).isEqualTo(1);

        WalletSync.listener(cached).onRemoteChange(new BalanceChanged("peer-2", OWNER.uuid(), "coins"));

        cached.findByOwner(OWNER); // the dropped owner reloads from the delegate
        assertThat(delegate.reads.get()).isEqualTo(2);
    }

    @Test
    void aFrameForAnotherContextLeavesTheCacheUntouched() {
        CountingDelegate delegate = new CountingDelegate();
        CachedWalletRepository cached = new CachedWalletRepository(delegate);

        cached.findByOwner(OWNER);
        assertThat(delegate.reads.get()).isEqualTo(1);

        WalletSync.listener(cached).onRemoteChange(new HomeChanged("peer-2", OWNER.uuid()));

        cached.findByOwner(OWNER); // still cached. A non-balance frame does not invalidate
        assertThat(delegate.reads.get()).isEqualTo(1);
    }

    /** A delegate that counts owner reads and serves an empty wallet, so cache hits are observable as read counts. */
    private static final class CountingDelegate implements WalletRepository {

        private final Map<UUID, Wallet> stored = new ConcurrentHashMap<>();
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            reads.incrementAndGet();
            return Optional.of(stored.computeIfAbsent(owner.uuid(), id -> Wallet.empty(owner)));
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return stored.computeIfAbsent(owner.uuid(), id -> Wallet.empty(owner));
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {}

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
            return Result.ok();
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }
    }
}
