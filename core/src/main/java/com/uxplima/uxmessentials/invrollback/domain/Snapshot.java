package com.uxplima.uxmessentials.invrollback.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * A single frozen copy of a player's inventory at a moment of interest. The queryable facts, the id, the owner,
 * the {@link SnapshotCause}, and the capture instant. Are first-class; the inventory itself is intrinsically
 * opaque payload, held here as the adapter's already-serialized {@code byte[]} (the architecture persistence
 * invariant: the domain never sees a Bukkit {@code ItemStack}, the adapter encodes the live inventory into these
 * bytes on capture and decodes them back on restore).
 *
 * <p>Modelled as a class rather than a record because the payload is a {@code byte[]}: the bytes are defensively
 * copied in and out and compared by value, so the aggregate stays immutable and equals/hashCode behave. Every
 * snapshot carries an id from creation. {@link #capture} mints a fresh one for a new capture, {@link #of}
 * rehydrates one read back from storage.
 */
public final class Snapshot {

    private final SnapshotId id;
    private final UUID owner;
    private final SnapshotCause cause;
    private final Instant createdAt;
    private final byte[] contents;

    private Snapshot(SnapshotId id, UUID owner, SnapshotCause cause, Instant createdAt, byte[] contents) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.contents = Objects.requireNonNull(contents, "contents").clone();
    }

    /** Capture a new snapshot, minting a fresh id, from an owner, a cause, an instant, and serialized contents. */
    public static Snapshot capture(UUID owner, SnapshotCause cause, Instant createdAt, byte[] contents) {
        return new Snapshot(SnapshotId.random(), owner, cause, createdAt, contents);
    }

    /** Rehydrate a stored snapshot with its persisted id. */
    public static Snapshot of(SnapshotId id, UUID owner, SnapshotCause cause, Instant createdAt, byte[] contents) {
        return new Snapshot(id, owner, cause, createdAt, contents);
    }

    public SnapshotId id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public SnapshotCause cause() {
        return cause;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** A copy of the opaque serialized inventory payload. */
    public byte[] contents() {
        return contents.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Snapshot snapshot
                && id.equals(snapshot.id)
                && owner.equals(snapshot.owner)
                && cause == snapshot.cause
                && createdAt.equals(snapshot.createdAt)
                && Arrays.equals(contents, snapshot.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, owner, cause, createdAt, Arrays.hashCode(contents));
    }

    @Override
    public String toString() {
        return "Snapshot[id=" + id + ", owner=" + owner + ", cause=" + cause + ", createdAt=" + createdAt
                + ", contents=" + contents.length + " bytes]";
    }
}
