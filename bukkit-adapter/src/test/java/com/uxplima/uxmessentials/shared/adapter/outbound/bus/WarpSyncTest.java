package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.persistence.warps.CachedWarpRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/**
 * Pins the surviving cross-server warp invalidation path now that the bespoke {@code RedisWarpSync}
 * side-channel is gone: a remote {@link WarpChanged} frame routed to {@link WarpSync#listener} must drop the
 * cached warp set so the next {@code /warp} on this backend reloads the authoritative rows from the shared
 * database. A frame of another type must leave the cached set in place. This invalidation used to be covered
 * only through the deleted Redis broadcaster.
 */
class WarpSyncTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WarpName SPAWN = WarpName.of("spawn");
    private static final WarpName SHOP = WarpName.of("shop");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");

    private static Warp warp(WarpName name) {
        return Warp.create(name, Position.of(WORLD, 1, 64, 1), OWNER, Instant.ofEpochMilli(1_000));
    }

    @Test
    void aRemoteWarpChangedDropsTheCachedSetSoTheNextReadReloads() {
        CountingDelegate delegate = new CountingDelegate();
        delegate.store(warp(SPAWN));
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        cached.all(); // warm load from the delegate
        delegate.store(warp(SHOP)); // a peer wrote a new warp straight to the shared database
        assertThat(cached.exists(SHOP)).isFalse(); // not yet visible. Still serving the loaded set

        WarpSync.listener(cached).onRemoteChange(new WarpChanged("peer-2", "shop"));

        assertThat(cached.exists(SHOP)).isTrue(); // the next read reloaded and now sees the peer's warp
        assertThat(delegate.reads.get()).isEqualTo(2);
    }

    @Test
    void aFrameForAnotherContextLeavesTheCachedSetInPlace() {
        CountingDelegate delegate = new CountingDelegate();
        delegate.store(warp(SPAWN));
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        cached.all(); // warm load
        delegate.store(warp(SHOP));

        WarpSync.listener(cached).onRemoteChange(new HomeChanged("peer-2", OWNER.uuid()));

        assertThat(cached.exists(SHOP)).isFalse(); // a non-warp frame never drops the set
        assertThat(delegate.reads.get()).isEqualTo(1);
    }

    /** A delegate that counts {@code all} reads and holds warps, so cache reloads are observable. */
    private static final class CountingDelegate implements WarpRepository {

        private final Map<String, Warp> stored = new ConcurrentHashMap<>();
        private final AtomicInteger reads = new AtomicInteger();

        void store(Warp warp) {
            stored.put(warp.name().value(), warp);
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(stored.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            reads.incrementAndGet();
            return new ArrayList<>(stored.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return stored.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            store(Objects.requireNonNull(warp, "warp"));
        }

        @Override
        public void delete(WarpName name) {
            stored.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }
}
