package com.uxplima.uxmessentials.persistence.npc;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the npc context's persistence adapter, so the consuming bukkit-adapter wires an
 * {@link NpcRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath). The
 * returned repository is the cached jOOQ adapter, write-through at the database, invalidate in the Caffeine
 * cache.
 */
@NullMarked
public final class NpcRepositories {

    private NpcRepositories() {}

    /** A cached jOOQ {@link NpcRepository} over the shared persistence DSL. */
    public static NpcRepository cached(Persistence persistence) {
        return cachedConcrete(persistence);
    }

    /**
     * As {@link #cached(Persistence)} but returned as its concrete decorator type, so the wiring can hand the
     * cross-server bus a per-name reload hook on the same cache the {@code /npc} commands and the renderer read
     * a remote NPC change reloads exactly that name from the shared DB and the listener re-renders it. Same
     * backing as {@link #cached}; this overload exposes the decorator only so the sync seam can reach it.
     */
    public static CachedNpcRepository cachedConcrete(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedNpcRepository(new JooqNpcRepository(persistence.dsl()));
    }
}
