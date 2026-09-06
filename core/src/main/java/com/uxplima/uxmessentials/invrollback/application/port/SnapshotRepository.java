package com.uxplima.uxmessentials.invrollback.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;

/**
 * Outbound port for durable inventory-snapshot storage. A snapshot's queryable facts (id, owner, cause, capture
 * instant) are first-class columns and only its serialized inventory is opaque payload, but that split is the
 * adapter's concern; the application sees the whole {@link Snapshot}. Snapshots are DB-backed and survive a world
 * rollback (the same hard invariant the economy ledger holds), never PDC. The jOOQ adapter implements this; the
 * use cases depend only on the contract.
 */
public interface SnapshotRepository {

    /** Persist a freshly captured snapshot. Its id is client-minted, so this is always an insert. */
    void save(Snapshot snapshot);

    /** Every snapshot the owner has, newest first: backs the restore listing and the retention sweep. */
    List<Snapshot> list(UUID owner);

    /**
     * The distinct owners that currently hold at least one snapshot. The seam the scheduled retention sweep
     * iterates so it can apply the per-player count cap ({@link #deleteBeyondCount}) across every player without
     * enumerating players it has no snapshots for. Order is unspecified.
     */
    List<UUID> owners();

    /** The snapshot with this id, or empty when none exists (already pruned or restored). */
    Optional<Snapshot> find(SnapshotId id);

    /** Remove the snapshot at {@code id}. Idempotent: deleting an id with no row is a no-op, never an error. */
    void delete(SnapshotId id);

    /**
     * Keep only the owner's newest {@code keep} snapshots, deleting the rest, and return the number removed. This
     * is the write-time count cap a capture applies so a player's snapshots never grow without bound.
     *
     * @param keep the number of most-recent snapshots to retain; {@code 0} removes them all
     */
    int deleteBeyondCount(UUID owner, int keep);

    /**
     * Delete every snapshot captured strictly before {@code cutoff}, returning the number removed: the age-based
     * half of the retention sweep.
     */
    int deleteOlderThan(Instant cutoff);
}
