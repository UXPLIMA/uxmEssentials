package com.uxplima.uxmessentials.persistence.worlds;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;

/**
 * Factory for the worlds context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link WorldRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the cached jOOQ adapter. Write-through at the database, applied to
 * the in-memory snapshot.
 */
public final class WorldRepositories {

    private WorldRepositories() {}

    /** A cached jOOQ {@link WorldRepository} over the shared persistence DSL. */
    public static WorldRepository cached(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedWorldRepository(new JooqWorldRepository(persistence.dsl()));
    }

    /**
     * The cached jOOQ {@link WorldRepository} as its concrete decorator type, so the wiring can hand the
     * enable-time reconciliation an invalidation hook on the same snapshot the commands read. Same backing as
     * {@link #cached}; this overload exposes the decorator only so the invalidation seam can reach it.
     */
    public static CachedWorldRepository cachedConcrete(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedWorldRepository(new JooqWorldRepository(persistence.dsl()));
    }
}
