package com.uxplima.uxmessentials.persistence.vaults;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;

/**
 * A Caffeine read-cache decorator over a delegate {@link VaultRepository}, keyed by {@link VaultId}. An open
 * resolves the vault's serialized contents once and is served from memory until a save to that vault
 * invalidates the entry, write-through at the delegate, invalidate here, never a write-back cache that could
 * lose a mutation. The durable source of truth is always the delegate; this only spares re-reading the same
 * vault's payload when a player re-opens it.
 *
 * <p>Only the keyed {@link #find} read is cached: a vault is opened far more often than its owner's index
 * listing changes, and the {@link #ownedIndices}/{@link #summaries}/{@link #count} reads are cheap index scans
 * that must stay fresh the instant a new vault is allocated or its name/icon changes, so they pass straight
 * through. A {@link #save} invalidates the
 * saved id only, and a {@link #delete} the deleted id only; a different vault of the same owner keeps its
 * cached payload.
 */
public final class CachedVaultRepository implements VaultRepository {

    private static final long DEFAULT_MAX_VAULTS = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final VaultRepository delegate;
    private final Cache<VaultId, Optional<Vault>> cache;

    public CachedVaultRepository(VaultRepository delegate) {
        this(delegate, DEFAULT_MAX_VAULTS, DEFAULT_TTL);
    }

    public CachedVaultRepository(VaultRepository delegate, long maximumVaults, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumVaults)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public Optional<Vault> find(VaultId id) {
        Objects.requireNonNull(id, "id");
        return cache.get(id, delegate::find);
    }

    @Override
    public List<Integer> ownedIndices(PlayerRef owner) {
        return delegate.ownedIndices(owner);
    }

    @Override
    public List<VaultSummary> summaries(PlayerRef owner) {
        return delegate.summaries(owner);
    }

    @Override
    public int count(PlayerRef owner) {
        return delegate.count(owner);
    }

    @Override
    public void save(Vault vault) {
        Objects.requireNonNull(vault, "vault");
        delegate.save(vault);
        cache.invalidate(vault.id());
    }

    @Override
    public void delete(VaultId id) {
        Objects.requireNonNull(id, "id");
        delegate.delete(id);
        cache.invalidate(id);
    }

    @Override
    public int deleteUntouchedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        int purged = delegate.deleteUntouchedBefore(cutoff);
        // A bulk purge cannot enumerate the removed ids, so drop the whole cache rather than guess which
        // entries survived; the rare admin-triggered sweep makes a full clear acceptable.
        cache.invalidateAll();
        return purged;
    }

    /** Drop every cached vault; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Drop one cached vault so the next open reloads its contents from the database. Called by the
     * cross-server bus client when a peer reports this vault changed on another backend. The shared DB already
     * holds the authoritative contents, so dropping the cached payload is all that is needed for the next open
     * on this backend to reflect it.
     */
    public void invalidate(VaultId id) {
        cache.invalidate(Objects.requireNonNull(id, "id"));
    }
}
