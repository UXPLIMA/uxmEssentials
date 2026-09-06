package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.playerwarps.CachedPlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.RentReminderCandidate;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.PlayerWarpChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The player-warps context's cross-server sync seam. The same shape as {@link HomeSync}, keyed by the same
 * {@link CachedPlayerWarpRepository} the {@code /pwarp} commands read. It does two things:
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository(CachedPlayerWarpRepository, BusPublisher)} wraps the cached
 *       repository so every local player-warp write (a {@code save}, a {@code /setpwarp}, a move/relocate, or
 *       a visibility flip all upsert the same row, or a {@code delete}) publishes a {@link PlayerWarpChanged}
 *       frame after the durable write commits, so peers learn the owner's warps changed.
 *   <li><b>Inbound</b>: {@link #listener(CachedPlayerWarpRepository)} returns a {@link RemoteSyncListener}
 *       that, on a remote {@code PlayerWarpChanged}, invalidates exactly that owner's cached set so the next
 *       {@code /pwarp} on this backend resolves the fresh warp from the shared DB.
 * </ul>
 *
 * <p>The decorator wraps the <em>same</em> cache the player-warp commands read, so the loop closes: a write
 * here emits a frame, the peer's listener drops the matching owner there. This is {@link HomeSync} copied for
 * the per-owner player-warp set, a write decorator plus an invalidation listener over its own cached
 * repository.
 */
@NullMarked
public final class PlayerWarpSync {

    private PlayerWarpSync() {}

    /** A {@link PlayerWarpRepository} that broadcasts a {@link PlayerWarpChanged} after every local write. */
    public static PlayerWarpRepository repository(CachedPlayerWarpRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /** A listener that invalidates the affected owner's cached set on a remote {@link PlayerWarpChanged}. */
    public static RemoteSyncListener listener(CachedPlayerWarpRepository cache) {
        Objects.requireNonNull(cache, "cache");
        return message -> {
            if (message instanceof PlayerWarpChanged changed) {
                cache.invalidateOwner(changed.owner());
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each mutation on the bus. */
    private static final class Broadcasting implements PlayerWarpRepository {

        private final PlayerWarpRepository delegate;
        private final BusPublisher bus;

        Broadcasting(PlayerWarpRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return delegate.findByName(name);
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return delegate.findById(id);
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return delegate.ownedBy(owner);
        }

        @Override
        public List<PlayerWarp> all() {
            return delegate.all();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return delegate.publicOwnedBy(owner);
        }

        @Override
        public int count(PlayerRef owner) {
            return delegate.count(owner);
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return delegate.existsByName(name);
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            PlayerWarpId id = delegate.save(warp);
            announce(warp.owner());
            return id;
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            // The frame is keyed by owner, but a delete-by-id no longer carries the owner directly. Resolve the warp
            // through the delegate before removing it so the announcement still names the owner whose cached set
            // peers must drop; a no-op when the id is already gone (there is nothing to announce).
            Optional<PlayerWarp> existing = delegate.findById(id);
            delegate.deleteById(id);
            existing.ifPresent(warp -> announce(warp.owner()));
        }

        @Override
        public Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
            return delegate.peekOwned(owner);
        }

        @Override
        public void recordVisit(PlayerWarpId id) {
            // A visit count is high-frequency, eventually-consistent data; letting peers drift a few visits
            // behind until the owner's next real change is fine, and not worth a cluster-wide invalidation per
            // teleport, so this forwards to the delegate without announcing.
            delegate.recordVisit(id);
        }

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {
            // The rating rollup is eventually-consistent denormalised data like the visit count; forward it without a
            // cluster-wide announce, letting peers pick it up on the owner's next real change.
            delegate.updateRating(id, summary);
        }

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {
            // Same reasoning as updateRating: an eventually-consistent counter, forwarded without announcing.
            delegate.refreshFavouriteCount(id);
        }

        @Override
        public List<PlayerWarp> dueForRent(Instant now, int limit) {
            // A cross-owner sweep read; forward straight through: the rent charge's own save() announces the flip.
            return delegate.dueForRent(now, limit);
        }

        @Override
        public List<PlayerWarp> suspendedForRent(int limit) {
            return delegate.suspendedForRent(limit);
        }

        @Override
        public List<RentReminderCandidate> remindableForRent(Instant now, Instant horizon, int maxStage, int limit) {
            return delegate.remindableForRent(now, horizon, maxStage, limit);
        }

        @Override
        public void markRentReminded(PlayerWarpId id, int stage) {
            // A persistence-only dedup counter, not a fact on the aggregate, so it neither invalidates nor announces.
            delegate.markRentReminded(id, stage);
        }

        private void announce(PlayerRef owner) {
            NetworkMessage frame = new PlayerWarpChanged(bus.serverId(), owner.uuid());
            bus.publish(frame);
        }
    }
}
