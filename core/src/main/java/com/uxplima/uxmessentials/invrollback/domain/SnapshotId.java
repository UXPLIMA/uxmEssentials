package com.uxplima.uxmessentials.invrollback.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The identity of a {@link Snapshot}. Every snapshot carries an id from the moment it is captured. The id is a
 * client-minted {@link UUID}, not a database-assigned autoincrement, because a UUID is unique before the row is
 * written (no read-back) and portable across every backend (the whole schema keys on canonical 36-char UUID
 * text, and no dialect-specific {@code AUTOINCREMENT}/{@code SERIAL}/{@code IDENTITY} is in the portable subset).
 *
 * @param value the snapshot's unique identifier
 */
public record SnapshotId(UUID value) {

    public SnapshotId {
        Objects.requireNonNull(value, "value");
    }

    /** Mint a fresh, unique id for a newly captured snapshot. */
    public static SnapshotId random() {
        return new SnapshotId(UUID.randomUUID());
    }

    /** Wrap an existing id (e.g. rehydrated from a stored row). */
    public static SnapshotId of(UUID value) {
        return new SnapshotId(value);
    }

    /** Parse an id from its canonical 36-char text form (the stored representation). */
    public static SnapshotId parse(String text) {
        Objects.requireNonNull(text, "text");
        return new SnapshotId(UUID.fromString(text));
    }

    /** The canonical 36-char text form, as stored. */
    public String asText() {
        return value.toString();
    }
}
