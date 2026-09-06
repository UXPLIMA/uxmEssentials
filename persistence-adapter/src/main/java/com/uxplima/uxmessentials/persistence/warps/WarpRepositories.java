package com.uxplima.uxmessentials.persistence.warps;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the warps context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link WarpRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the cached jOOQ adapter, write-through at the database,
 * invalidate in the Caffeine cache.
 */
@NullMarked
public final class WarpRepositories {

    private WarpRepositories() {}

    /** A cached jOOQ {@link WarpRepository} over the shared persistence DSL. */
    public static WarpRepository cached(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedWarpRepository(new JooqWarpRepository(persistence.dsl()));
    }

    /**
     * The cached jOOQ {@link WarpRepository} as its concrete decorator type, so the wiring can hand the
     * cross-server bus an invalidation hook on the same cache the commands read, a remote {@code /setwarp}
     * drops the cached warp set. Same backing as {@link #cached}; this overload exposes the decorator only so
     * the invalidation seam can reach it.
     */
    public static CachedWarpRepository cachedConcrete(Persistence persistence) {
        return cachedConcrete(persistence, java.util.UUID::toString);
    }

    /**
     * As {@link #cachedConcrete(Persistence)} but resolving each warp owner's display name through {@code names}
     * (an adapter-supplied uuid-to-name profile lookup), so the warp carries the live owner name instead of the
     * uuid string. The display wiring passes a real resolver; non-display callers keep the uuid default above.
     */
    public static CachedWarpRepository cachedConcrete(
            Persistence persistence, java.util.function.Function<java.util.UUID, String> names) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(names, "names");
        return new CachedWarpRepository(new JooqWarpRepository(persistence.dsl(), names));
    }

    /** A cached jOOQ {@link WarpCategoryRepository} over the shared persistence DSL. */
    public static WarpCategoryRepository categories(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedWarpCategoryRepository(new JooqWarpCategoryRepository(persistence.dsl()));
    }

    /**
     * The cached jOOQ {@link WarpCategoryRepository} as its concrete decorator type, so the wiring can hold a
     * reference to warm the set on enable and reach the invalidation seam. Same backing as {@link #categories}.
     */
    public static CachedWarpCategoryRepository categoriesConcrete(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedWarpCategoryRepository(new JooqWarpCategoryRepository(persistence.dsl()));
    }
}
