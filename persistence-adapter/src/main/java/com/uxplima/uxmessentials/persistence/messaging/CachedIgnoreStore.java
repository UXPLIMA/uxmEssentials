package com.uxplima.uxmessentials.persistence.messaging;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A Caffeine read-cache decorator over a delegate {@link IgnoreStore}, keyed by owner uuid. The ignore list
 * is read on the hot {@code /msg} and {@code /mail send} delivery paths (an ignore-aware lookup per message),
 * so a recipient's small list is served from memory after the first miss and re-loaded only when a write to
 * that owner invalidates the entry, write-through at the delegate, invalidate here, never a write-back cache
 * that could lose a mutation. The durable source of truth is always the delegate.
 */
public final class CachedIgnoreStore implements IgnoreStore {

    private static final long DEFAULT_MAX_OWNERS = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final IgnoreStore delegate;
    private final ReadThroughCache<UUID, IgnoreList> cache;

    public CachedIgnoreStore(IgnoreStore delegate) {
        this(delegate, DEFAULT_MAX_OWNERS, DEFAULT_TTL);
    }

    public CachedIgnoreStore(IgnoreStore delegate, long maximumOwners, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(this::loadFresh, maximumOwners, ttl);
    }

    @Override
    public IgnoreList load(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return cache.get(owner.uuid());
    }

    @Override
    public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
        delegate.ignore(owner, ignored, scope);
        cache.invalidate(Objects.requireNonNull(owner, "owner").uuid());
    }

    @Override
    public void unignore(PlayerRef owner, PlayerRef ignored) {
        delegate.unignore(owner, ignored);
        cache.invalidate(Objects.requireNonNull(owner, "owner").uuid());
    }

    /** Drop every cached owner; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Drop one cached owner so the next read reloads it from the database. Called by the cross-server bus
     * client when a peer reports this owner's ignore list changed on another backend.
     */
    public void invalidateOwner(UUID owner) {
        cache.invalidate(Objects.requireNonNull(owner, "owner"));
    }

    private IgnoreList loadFresh(UUID ownerUuid) {
        // The cache key is the owner uuid; the delegate only reads the uuid (the row carries no owner name),
        // so a name placeholder here never reaches a row.
        return delegate.load(new PlayerRef(ownerUuid, ownerUuid.toString()));
    }
}
