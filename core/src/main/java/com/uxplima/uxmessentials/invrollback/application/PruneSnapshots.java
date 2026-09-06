package com.uxplima.uxmessentials.invrollback.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.RetentionPolicy;

/**
 * The scheduled retention sweep that keeps {@code inv_snapshots} bounded. It applies the configured
 * {@link RetentionPolicy} through the repository's two prune primitives: the per-player count cap
 * ({@link SnapshotRepository#deleteBeyondCount}) across every owner that holds snapshots, and the global age
 * cutoff ({@link SnapshotRepository#deleteOlderThan}). The count cap is also enforced at write time on every
 * capture, so between sweeps only the age half accrues; the sweep additionally catches an owner left over the cap
 * after an operator lowers {@code max-per-player} at reload. Either limit set to zero is disabled and skipped.
 *
 * <p>Pure application code, no Bukkit, no clock of its own: the caller passes the reference instant, so the rule
 * stays deterministic and testable. The adapter runs {@link #sweep} off the tick thread through the injected
 * {@code Scheduler} (a global repeating tick that hands the DB work to the async lane), never on a region thread.
 */
public final class PruneSnapshots {

    private final SnapshotRepository repository;
    private final RetentionPolicy policy;

    public PruneSnapshots(SnapshotRepository repository, RetentionPolicy policy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Run one sweep as of {@code now}, returning the total number of snapshots removed. */
    public int sweep(Instant now) {
        Objects.requireNonNull(now, "now");
        int removed = 0;
        if (policy.maxPerPlayer() > 0) {
            for (UUID owner : repository.owners()) {
                removed += repository.deleteBeyondCount(owner, policy.maxPerPlayer());
            }
        }
        if (policy.maxAgeDays() > 0) {
            removed += repository.deleteOlderThan(now.minus(Duration.ofDays(policy.maxAgeDays())));
        }
        return removed;
    }
}
