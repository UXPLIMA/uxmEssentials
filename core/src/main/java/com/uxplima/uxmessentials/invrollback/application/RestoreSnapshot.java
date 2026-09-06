package com.uxplima.uxmessentials.invrollback.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;

/**
 * Restores one of a target's stored inventory snapshots, the application half of the {@code /invrestore} restore
 * action. It resolves the chosen snapshot, and before the caller overwrites the live inventory it captures the
 * target's <em>current</em> serialized inventory as a {@link SnapshotCause#RESTORE} safety copy (through the same
 * {@link CaptureSnapshot} use case, so the count cap applies), then hands the chosen snapshot back so the adapter
 * can decode it and set the live inventory. A stale id. A snapshot pruned or already restored between the GUI
 * open and the click: resolves to empty and captures nothing, so a dead click leaves no orphan safety row.
 *
 * <p>Pure application code: it never touches a Bukkit type. The adapter reads and re-applies the live inventory on
 * the target's entity thread and serializes it into the {@code currentContents} bytes it passes in; this use case
 * runs off the tick thread through the {@code Scheduler.async} port so the two DB touches (find + safety save)
 * never block a region thread.
 */
public final class RestoreSnapshot {

    private final SnapshotRepository repository;
    private final CaptureSnapshot safetyCapture;

    /**
     * @param repository the durable snapshot store the chosen snapshot is resolved from
     * @param safetyCapture the capture use case that freezes the pre-restore inventory as a RESTORE snapshot
     */
    public RestoreSnapshot(SnapshotRepository repository, CaptureSnapshot safetyCapture) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.safetyCapture = Objects.requireNonNull(safetyCapture, "safetyCapture");
    }

    /**
     * Resolve the snapshot {@code id} for {@code target}; when it exists, freeze {@code currentContents} as a
     * RESTORE safety snapshot dated {@code now} and return the chosen snapshot for the caller to apply. Returns
     * empty (capturing no safety copy) when the id no longer resolves.
     */
    public Optional<Snapshot> restore(UUID target, SnapshotId id, byte[] currentContents, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currentContents, "currentContents");
        Objects.requireNonNull(now, "now");
        Optional<Snapshot> chosen = repository.find(id);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        safetyCapture.capture(target, SnapshotCause.RESTORE, currentContents, now);
        return chosen;
    }
}
