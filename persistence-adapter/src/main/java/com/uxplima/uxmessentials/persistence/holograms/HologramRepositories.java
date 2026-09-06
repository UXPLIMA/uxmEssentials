package com.uxplima.uxmessentials.persistence.holograms;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the holograms context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link HologramRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the cached jOOQ adapter, write-through at the database, invalidate
 * in the Caffeine cache.
 */
@NullMarked
public final class HologramRepositories {

    private HologramRepositories() {}

    /** A cached jOOQ {@link HologramRepository} over the shared persistence DSL. */
    public static HologramRepository cached(Persistence persistence) {
        return cachedConcrete(persistence);
    }

    /**
     * As {@link #cached(Persistence)} but returned as its concrete decorator type, so the wiring can hand the
     * cross-server bus a per-name reload hook on the same cache the {@code /hologram} commands and the renderer
     * read: a remote hologram change reloads exactly that name from the shared DB and the listener re-renders it.
     * Same backing as {@link #cached}; this overload exposes the decorator only so the sync seam can reach it.
     */
    public static CachedHologramRepository cachedConcrete(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedHologramRepository(new JooqHologramRepository(persistence.dsl()));
    }
}
