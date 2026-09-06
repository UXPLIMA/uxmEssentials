package com.uxplima.uxmessentials.persistence.homes;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A Caffeine read-cache decorator over a delegate {@link HomeRepository}, keyed by owner uuid. A load
 * misses through to the database once and is served from memory until a write to that owner invalidates
 * the entry, write-through at the delegate, invalidate here, never a write-back cache that could lose a
 * mutation. The durable source of truth is always the delegate; this only spares repeated reads of a hot
 * owner's small set.
 *
 * <p>The cached value is the whole {@link HomeSet}, so a {@code /home} that needs the owner's homes and the
 * {@code /sethome} count check share one cached read. {@link #count} is derived from the cached set rather
 * than issuing a second {@code COUNT(*)}. {@link #findSlot} consults the cached set on a hit, avoiding a
 * round-trip for the common case of an already-loaded owner.
 */
public final class CachedHomeRepository implements HomeRepository {

    private static final long DEFAULT_MAX_OWNERS = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final HomeRepository delegate;
    private final ReadThroughCache<UUID, HomeSet> cache;

    public CachedHomeRepository(HomeRepository delegate) {
        this(delegate, DEFAULT_MAX_OWNERS, DEFAULT_TTL);
    }

    public CachedHomeRepository(HomeRepository delegate, long maximumOwners, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(this::loadFresh, maximumOwners, ttl);
    }

    @Override
    public HomeSet load(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return cache.get(owner.uuid());
    }

    @Override
    public int count(PlayerRef owner) {
        return load(owner).count();
    }

    @Override
    public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        // Use the cached set when available to avoid a round-trip; fall back to the delegate on a miss.
        return cache.getIfPresent(owner.uuid())
                .map(set -> set.find(slot))
                .orElseGet(() -> delegate.findSlot(owner, slot));
    }

    @Override
    public void save(Home home) {
        Objects.requireNonNull(home, "home");
        delegate.save(home);
        cache.invalidate(home.owner().uuid());
    }

    @Override
    public void deleteSlot(PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        delegate.deleteSlot(owner, slot);
        cache.invalidate(owner.uuid());
    }

    @Override
    public void deleteAll(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        delegate.deleteAll(owner);
        cache.invalidate(owner.uuid());
    }

    @Override
    public Optional<List<Home>> peek(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return cache.getIfPresent(owner.uuid()).map(HomeSet::all);
    }

    /** Drop every cached owner; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Drop one cached owner so the next read reloads it from the database. Called by the cross-server bus
     * client when a peer reports this owner's homes changed on another backend. The shared DB already holds
     * the authoritative rows, so dropping the cached set lets the next {@code /home} on this backend resolve
     * the fresh location.
     */
    public void invalidateOwner(UUID owner) {
        cache.invalidate(Objects.requireNonNull(owner, "owner"));
    }

    private HomeSet loadFresh(UUID ownerUuid) {
        // The cache key is the owner uuid; the loader needs a PlayerRef, and the delegate only reads the
        // uuid (the row carries no owner name), so a name placeholder here never reaches a row.
        return delegate.load(new PlayerRef(ownerUuid, ownerUuid.toString()));
    }
}
