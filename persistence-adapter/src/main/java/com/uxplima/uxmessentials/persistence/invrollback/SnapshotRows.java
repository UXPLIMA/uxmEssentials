package com.uxplima.uxmessentials.persistence.invrollback;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.persistence.jooq.tables.InvSnapshots;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.InvSnapshotsRecord;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between an {@code inv_snapshots} row and the domain {@link Snapshot}. The queryable
 * facts, the id (canonical 36-char text), the owner uuid, the cause constant name, and the created-at epoch
 * millis. Are first-class columns; only the serialized inventory is opaque payload, stored as the base64 text of
 * the adapter's already-serialized item bytes (the architecture persistence invariant). This class is the single
 * place that translation lives, so the row shape stays identical on every backend.
 *
 * <p>A null {@code contents} cell is a capture of an empty inventory; it round-trips to an empty byte array and
 * back to a null cell, so no blob is written for a snapshot that holds nothing.
 */
final class SnapshotRows {

    private SnapshotRows() {}

    /** Rebuild a {@link Snapshot} from a queried row. */
    static Snapshot toSnapshot(Record row) {
        InvSnapshots t = InvSnapshots.INV_SNAPSHOTS;
        SnapshotId id = SnapshotId.parse(row.get(t.ID));
        UUID owner = UUID.fromString(row.get(t.OWNER));
        SnapshotCause cause = SnapshotCause.valueOf(row.get(t.CAUSE));
        Instant createdAt = Instant.ofEpochMilli(row.get(t.CREATED_AT));
        return Snapshot.of(id, owner, cause, createdAt, decode(row.get(t.CONTENTS)));
    }

    /** Populate an {@link InvSnapshotsRecord} from a domain {@link Snapshot} for an insert. */
    static void apply(InvSnapshotsRecord record, Snapshot snapshot) {
        record.setId(snapshot.id().asText())
                .setOwner(snapshot.owner().toString())
                .setCause(snapshot.cause().name())
                .setCreatedAt(snapshot.createdAt().toEpochMilli())
                .setContents(encode(snapshot.contents()));
    }

    /** Base64 the opaque payload, or {@code null} for an empty capture so no blob is written. */
    static @Nullable String encode(byte[] contents) {
        return contents.length == 0 ? null : Base64.getEncoder().encodeToString(contents);
    }

    /** Decode a stored base64 cell back into opaque bytes; a null or blank cell is an empty capture. */
    static byte[] decode(@Nullable String cell) {
        if (cell == null || cell.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(cell);
    }
}
