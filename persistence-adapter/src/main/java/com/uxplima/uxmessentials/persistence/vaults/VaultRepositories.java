package com.uxplima.uxmessentials.persistence.vaults;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the vaults context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link VaultRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath). The
 * returned repository is the cached jOOQ adapter, write-through at the database, invalidate in the Caffeine
 * cache.
 */
@NullMarked
public final class VaultRepositories {

    private VaultRepositories() {}

    /** A cached jOOQ {@link VaultRepository} over the shared persistence DSL. */
    public static VaultRepository cached(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedVaultRepository(new JooqVaultRepository(persistence.dsl()));
    }

    /**
     * The cached jOOQ {@link VaultRepository} as its concrete decorator type, so the wiring can hand the
     * cross-server bus a per-vault invalidation hook on the same cache the GUI reads, a remote vault save
     * drops exactly that vault's cached payload. Same backing as {@link #cached}; this overload exposes the
     * decorator only so the invalidation seam can reach it.
     */
    public static CachedVaultRepository cachedConcrete(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedVaultRepository(new JooqVaultRepository(persistence.dsl()));
    }
}
