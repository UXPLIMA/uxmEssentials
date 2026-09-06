package com.uxplima.uxmessentials.economy.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the economy's data-maintenance job: trimming stale transaction telemetry and purging the
 * wallets of long-inactive players, so the database stays small and {@code /baltop} stays honest. Every method
 * is a bounded query the maintenance task runs off-thread; the destructive ones are only ever called past the
 * task's {@code dry-run} gate.
 *
 * <p>The hard invariant: a wallet purge must never delete an owner the rest of the economy still references
 * one with an outstanding loan, a credit score, a bank membership, or a bank they created, because that money
 * and those relationships must survive. {@link #protectedOwners()} is that guard, computed from the live FK
 * graph; {@link #purgeOwners} deletes only the owners the caller has already filtered against it.
 */
public interface EconomyMaintenance {

    /** How many telemetry rows are older than {@code cutoffMillis} (the dry-run preview of {@link #deleteTransactionsBefore}). */
    int countTransactionsBefore(long cutoffMillis);

    /** Delete telemetry rows older than {@code cutoffMillis}; returns the number removed. */
    int deleteTransactionsBefore(long cutoffMillis);

    /** Every wallet owner's identity (uuid + last-known name), the candidate set the task filters by last-played. */
    List<PlayerRef> allOwners();

    /**
     * The owners that must never be purged. Those referenced by a loan, a credit score, a bank membership, or a
     * bank they created. The task subtracts this set from its inactive candidates before any delete.
     */
    Set<UUID> protectedOwners();

    /**
     * Delete the wallet rows, pay preferences, and owner identity of each given owner in one transaction; returns
     * the number of owner identities removed. The caller must have excluded {@link #protectedOwners()} already
     * this is the destructive step, run only when the task is not in dry-run mode.
     */
    int purgeOwners(Collection<UUID> owners);
}
