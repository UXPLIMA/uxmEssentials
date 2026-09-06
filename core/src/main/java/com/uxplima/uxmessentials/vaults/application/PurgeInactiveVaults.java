package com.uxplima.uxmessentials.vaults.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;

/**
 * Inactive-vault cleanup: delete every vault row not opened or saved within {@code inactiveFor}, reclaiming
 * storage held by players who have long since abandoned a vault. The cutoff is {@code now - inactiveFor}, and
 * the repository removes rows whose last-touched instant is before it (the same cutoff-delete idiom the mail
 * expiry sweep uses). The returned count is what the caller logs and audits.
 *
 * <p>On a network the sweep runs per-server against the one shared database, so a purge driven by any peer is
 * correct everywhere: the rows are gone from the single backend, and a peer that next opens one of those ids
 * re-reads an empty vault straight from the DB rather than a stale local copy. There is no cross-server fan-out
 * to coordinate: the shared store is the source of truth, and the adapter only invalidates its own cache.
 *
 * <p>Pure: this use case computes the cutoff and delegates to the repository; the cache invalidation and the
 * sweep's scheduling are the adapter's concern.
 */
public final class PurgeInactiveVaults {

    private final VaultRepository repository;

    public PurgeInactiveVaults(VaultRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Purge every vault untouched for at least {@code inactiveFor} as of {@code now}, returning the number of
     * rows removed. A zero {@code inactiveFor} makes the cutoff {@code now} itself (everything strictly older
     * than this instant). {@code inactiveFor} must be non-negative.
     */
    public int purge(Duration inactiveFor, Instant now) {
        Objects.requireNonNull(inactiveFor, "inactiveFor");
        Objects.requireNonNull(now, "now");
        if (inactiveFor.isNegative()) {
            throw new IllegalArgumentException("inactiveFor must not be negative: " + inactiveFor);
        }
        return repository.deleteUntouchedBefore(now.minus(inactiveFor));
    }
}
