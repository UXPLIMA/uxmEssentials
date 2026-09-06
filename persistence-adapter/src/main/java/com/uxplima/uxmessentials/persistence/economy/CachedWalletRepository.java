package com.uxplima.uxmessentials.persistence.economy;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * A Caffeine read-cache decorator over the jOOQ {@link WalletRepository}, the offline-balance accelerator of
 * {@code docs/11-economy-integration.md} §9.6. {@code /balance <offline>} and {@code /baltop} are served from
 * a bounded, TTL'd cache so a tick-thread read of an offline player's balance does not hit the database; a
 * miss loads the whole wallet through once and a write invalidates the affected owner so a stale balance is
 * never shown after a mutation, write-through at the delegate, invalidate here, never a write-back cache.
 *
 * <p>The cache is a <strong>read accelerator only</strong>. It is never an authority and never written
 * through as a source of truth: the guarded debit/credit/transfer path always runs against the delegate
 * (the live DB row inside its transaction) and invalidates the cached owner afterwards, so invariant (d)
 * the database is the only authority. Stays intact. {@code top} is not cached here; the provider's per-
 * currency baltop snapshot owns that caching above the port.
 */
@NullMarked
public final class CachedWalletRepository implements WalletRepository {

    private static final long DEFAULT_MAX_OWNERS = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final WalletRepository delegate;
    private final ReadThroughCache<UUID, Optional<Wallet>> cache;

    public CachedWalletRepository(WalletRepository delegate) {
        this(delegate, DEFAULT_MAX_OWNERS, DEFAULT_TTL);
    }

    public CachedWalletRepository(WalletRepository delegate, long maximumOwners, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(this::loadFresh, maximumOwners, ttl);
    }

    @Override
    public Optional<Wallet> findByOwner(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return cache.get(owner.uuid());
    }

    @Override
    public Wallet ensureOwner(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        Wallet wallet = delegate.ensureOwner(owner);
        cache.invalidate(owner.uuid());
        return wallet;
    }

    @Override
    public void upsertBalance(PlayerRef owner, Money balance) {
        delegate.upsertBalance(owner, balance);
        cache.invalidate(Objects.requireNonNull(owner, "owner").uuid());
    }

    @Override
    public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
        Result<Unit, TransferError> result = delegate.transfer(from, to, amount);
        cache.invalidate(Objects.requireNonNull(from, "from").uuid());
        cache.invalidate(Objects.requireNonNull(to, "to").uuid());
        return result;
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Result<Unit, TransferError> result = delegate.debit(owner, amount);
        cache.invalidate(Objects.requireNonNull(owner, "owner").uuid());
        return result;
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Result<Unit, TransferError> result = delegate.credit(owner, amount);
        cache.invalidate(Objects.requireNonNull(owner, "owner").uuid());
        return result;
    }

    @Override
    public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
        Result<Unit, TransferError> result = delegate.exchange(owner, debit, credit);
        cache.invalidate(Objects.requireNonNull(owner, "owner").uuid());
        return result;
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        return delegate.top(currency, limit);
    }

    /** Drop every cached owner; call on a module reload or a bulk mutation. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Drop one cached owner so the next read reloads it from the database. Called by the cross-server bus
     * client when a peer reports this owner's balance changed on another backend. The shared DB already
     * holds the authoritative figure, so dropping the cached snapshot is all that is needed for the next
     * {@code /balance} on this backend to reflect it.
     */
    public void invalidateOwner(UUID owner) {
        cache.invalidate(Objects.requireNonNull(owner, "owner"));
    }

    private Optional<Wallet> loadFresh(UUID ownerUuid) {
        // The cache key is the owner uuid; the loader needs a PlayerRef and the delegate reads only the uuid
        // for the lookup (the row carries the name), so a name placeholder here never reaches a stored row.
        return delegate.findByOwner(new PlayerRef(ownerUuid, ownerUuid.toString()));
    }
}
