package com.uxplima.uxmessentials.persistence.playerwarps;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.RentReminderCandidate;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A Caffeine read-cache decorator over a delegate {@link PlayerWarpRepository}, keyed by owner uuid. The cached
 * value is a hot owner's whole warp set as an ordered name → warp map, loaded once on a miss and served from memory
 * until a write to that owner invalidates it, write-through at the delegate, invalidate here, never a write-back
 * cache that could lose a mutation. The delegate stays the durable source of truth; this only spares repeated reads
 * of the same owner's small set.
 *
 * <p>Only the owner-scoped reads are cache-derivable: {@link #ownedBy}, {@link #publicOwnedBy} (filtered from the
 * cached map), {@link #count} (its size), and {@link #peekOwned} (the tick-thread suggester path, which reads the
 * cached set on a hit and nothing on a miss without ever loading). Because warp names are now globally unique rather
 * than owner-scoped, a name is no longer owner-derivable, so {@link #findByName}, {@link #findById},
 * {@link #existsByName}, and the cross-owner {@link #all} pass straight through to the delegate, teleport
 * correctness beats a cache hit there.
 */
@NullMarked
public final class CachedPlayerWarpRepository implements PlayerWarpRepository {

    private static final long DEFAULT_MAX_OWNERS = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final PlayerWarpRepository delegate;
    private final ReadThroughCache<UUID, Map<String, PlayerWarp>> cache;

    public CachedPlayerWarpRepository(PlayerWarpRepository delegate) {
        this(delegate, DEFAULT_MAX_OWNERS, DEFAULT_TTL);
    }

    public CachedPlayerWarpRepository(PlayerWarpRepository delegate, long maximumOwners, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(this::loadFresh, maximumOwners, ttl);
    }

    @Override
    public Optional<PlayerWarp> findByName(PlayerWarpName name) {
        Objects.requireNonNull(name, "name");
        // A global name is not owner-derivable; go straight to the durable store.
        return delegate.findByName(name);
    }

    @Override
    public Optional<PlayerWarp> findById(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        return delegate.findById(id);
    }

    @Override
    public List<PlayerWarp> ownedBy(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return List.copyOf(snapshot(owner).values());
    }

    @Override
    public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<PlayerWarp> shown = new ArrayList<>();
        for (PlayerWarp warp : snapshot(owner).values()) {
            if (warp.status() == WarpStatus.ACTIVE && warp.access() == WarpAccess.PUBLIC) {
                shown.add(warp);
            }
        }
        return List.copyOf(shown);
    }

    @Override
    public List<PlayerWarp> all() {
        // The cross-owner admin scan is not per-owner cacheable; read it straight from the delegate.
        return delegate.all();
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return snapshot(owner).size();
    }

    @Override
    public boolean existsByName(PlayerWarpName name) {
        Objects.requireNonNull(name, "name");
        return delegate.existsByName(name);
    }

    @Override
    public PlayerWarpId save(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        PlayerWarpId id = delegate.save(warp);
        cache.invalidate(warp.owner().uuid());
        return id;
    }

    @Override
    public void deleteById(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        // Resolve the owner before the delete so we know which cached owner set to drop; a no-op when the id is gone.
        Optional<PlayerWarp> existing = delegate.findById(id);
        delegate.deleteById(id);
        existing.ifPresent(warp -> cache.invalidate(warp.owner().uuid()));
    }

    @Override
    public void recordVisit(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        // Eventually consistent per the port contract: bump the durable counter but do not invalidate: a cached
        // owner set drifting by a few visits until its next real write is acceptable and spares a cache churn.
        delegate.recordVisit(id);
    }

    @Override
    public void updateRating(PlayerWarpId id, RatingSummary summary) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(summary, "summary");
        // Eventually consistent like recordVisit: write the durable rollup but do not invalidate. The rater is not
        // the owner, so we would have to resolve the owner just to know whose cached set to drop, and a cached rating
        // drifting until the owner's next real write is acceptable for a rare vote.
        delegate.updateRating(id, summary);
    }

    @Override
    public void refreshFavouriteCount(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        // Same as updateRating: a durable, eventually-consistent denormalised counter that does not justify a cache
        // churn on the owner's set.
        delegate.refreshFavouriteCount(id);
    }

    @Override
    public List<PlayerWarp> dueForRent(Instant now, int limit) {
        // A cross-owner sweep read, not per-owner cacheable; read it straight from the durable store.
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
        Objects.requireNonNull(id, "id");
        // rent_reminded_stage is persistence-only, never a fact on the cached aggregate, so a bump cannot make the
        // cached owner set stale; forward it like recordVisit without invalidating.
        delegate.markRentReminded(id, stage);
    }

    @Override
    public Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return cache.getIfPresent(owner.uuid()).map(byName -> List.copyOf(byName.values()));
    }

    /** Drop every cached owner; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Drop one cached owner so the next read reloads it from the database. Called by the cross-server bus client
     * when a peer reports this owner's player-warps changed on another backend.
     */
    public void invalidateOwner(UUID owner) {
        cache.invalidate(Objects.requireNonNull(owner, "owner"));
    }

    private Map<String, PlayerWarp> snapshot(PlayerRef owner) {
        return cache.get(owner.uuid());
    }

    private Map<String, PlayerWarp> loadFresh(UUID ownerUuid) {
        // The cache key is the owner uuid; the delegate keys its query on the uuid alone, so the name placeholder on
        // this PlayerRef never reaches a row.
        Map<String, PlayerWarp> byName = new LinkedHashMap<>();
        for (PlayerWarp warp : delegate.ownedBy(new PlayerRef(ownerUuid, ownerUuid.toString()))) {
            byName.put(warp.name().value(), warp);
        }
        return byName;
    }
}
