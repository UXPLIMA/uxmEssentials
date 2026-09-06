package com.uxplima.uxmessentials.persistence.teleport;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;
import org.jspecify.annotations.NullMarked;

/**
 * A Caffeine read-cache in front of the jOOQ {@link RtpPoolStore}, caching the per-world {@code count}, the one
 * scalar read repeatedly (the cap/health checks) while the pool refills, so a repeated count does not re-hit the
 * single-writer SQLite backend. The cache is bounded and short-lived, and every write invalidates it: a {@code save}
 * drops the world's count, a {@code deleteStale} drops all of them, so the next read reflects the durable state
 * (write-through at the database, invalidate here: never a write-back cache that could lose a mutation).
 *
 * <p>{@link #load} is intentionally not cached: it is the one-shot startup pre-warm read and must always reflect the
 * freshest persisted set, so it passes straight through to the delegate.
 */
@NullMarked
public final class CachedRtpPoolStore implements RtpPoolStore {

    // A handful of worlds at most; the entry expires quickly and every write invalidates it, so this only spares a
    // repeated count within a single refill burst.
    private static final long MAX_WORLDS = 64;
    private static final Duration COUNT_TTL = Duration.ofSeconds(30);

    private final RtpPoolStore delegate;
    private final ReadThroughCache<WorldRef, Integer> counts;

    public CachedRtpPoolStore(RtpPoolStore delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.counts = ReadThroughCache.create(delegate::count, MAX_WORLDS, COUNT_TTL);
    }

    @Override
    public void save(WorldRef world, Collection<RtpColumn> columns) {
        Objects.requireNonNull(world, "world");
        delegate.save(world, columns);
        counts.invalidate(world);
    }

    @Override
    public List<RtpColumn> load(WorldRef world, int limit) {
        return delegate.load(world, limit);
    }

    @Override
    public List<RtpColumn> loadByBiome(WorldRef world, BiomeName biome, int limit) {
        return delegate.loadByBiome(world, biome, limit);
    }

    @Override
    public int deleteStale(Duration olderThan) {
        int removed = delegate.deleteStale(olderThan);
        counts.invalidateAll();
        return removed;
    }

    @Override
    public int count(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return counts.get(world);
    }
}
