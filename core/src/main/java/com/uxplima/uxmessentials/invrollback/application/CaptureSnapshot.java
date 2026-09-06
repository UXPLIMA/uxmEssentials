package com.uxplima.uxmessentials.invrollback.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;

/**
 * Records one inventory snapshot: it builds the {@link Snapshot} aggregate from the owner, cause, capture instant
 * and the adapter's already-serialized inventory bytes, persists it through the {@link SnapshotRepository}, then
 * enforces the per-player count cap at write time so a player's snapshots stay bounded (the age-based half of
 * retention runs on the scheduled sweep in a later phase).
 *
 * <p>Pure application code: it never touches a Bukkit type. The inbound listener reads the live inventory on the
 * tick thread, serializes it, and calls {@link #capture} off the tick thread through the {@code Scheduler.async}
 * port, so the DB write never blocks a region thread.
 */
public final class CaptureSnapshot {

    private final SnapshotRepository repository;
    private final int maxPerPlayer;

    /**
     * @param repository the durable snapshot store
     * @param maxPerPlayer the count cap enforced after each save; {@code <= 0} leaves the count unbounded here
     *     (the scheduled age sweep still trims by age)
     */
    public CaptureSnapshot(SnapshotRepository repository, int maxPerPlayer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.maxPerPlayer = maxPerPlayer;
    }

    /** Capture and persist a snapshot of {@code contents} for {@code owner}, then trim to the count cap. */
    public Snapshot capture(UUID owner, SnapshotCause cause, byte[] contents, Instant createdAt) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(createdAt, "createdAt");
        Snapshot snapshot = Snapshot.capture(owner, cause, createdAt, contents);
        repository.save(snapshot);
        if (maxPerPlayer > 0) {
            repository.deleteBeyondCount(owner, maxPerPlayer);
        }
        return snapshot;
    }
}
