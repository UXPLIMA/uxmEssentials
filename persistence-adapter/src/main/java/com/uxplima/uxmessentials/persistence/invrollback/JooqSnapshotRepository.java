package com.uxplima.uxmessentials.persistence.invrollback;

import static com.uxplima.uxmessentials.persistence.jooq.tables.InvSnapshots.INV_SNAPSHOTS;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.InvSnapshotsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link SnapshotRepository} over the generated {@code INV_SNAPSHOTS} table. A {@code save}
 * inserts one row keyed by the snapshot's client-minted UUID (never an upsert. Every capture is a fresh id); the
 * listing reads an owner's snapshots newest-first off the {@code idx_inv_snapshots_owner} index; a {@code find}
 * resolves one by id; a {@code delete} removes the one row, idempotent on a missing id. {@code owners} answers the
 * distinct owner uuids the retention sweep iterates. {@code deleteBeyondCount}
 * keeps only the owner's newest {@code keep} rows. It reads the owner's ids by recency and deletes those past the
 * cap in a single transaction, a form portable to every backend (no same-table subquery in a {@code DELETE},
 * which MySQL forbids). {@code deleteOlderThan} removes every row captured before the cutoff. Every statement is
 * typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqSnapshotRepository extends JooqRepository implements SnapshotRepository {

    public JooqSnapshotRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void save(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        write(dsl -> {
            InvSnapshotsRecord record = dsl.newRecord(INV_SNAPSHOTS);
            SnapshotRows.apply(record, snapshot);
            dsl.insertInto(INV_SNAPSHOTS).set(record).execute();
            return null;
        });
    }

    @Override
    public List<Snapshot> list(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(INV_SNAPSHOTS)
                .where(INV_SNAPSHOTS.OWNER.eq(owner.toString()))
                .orderBy(INV_SNAPSHOTS.CREATED_AT.desc(), INV_SNAPSHOTS.ID.desc())
                .fetch(SnapshotRows::toSnapshot));
    }

    @Override
    public List<UUID> owners() {
        return read(dsl -> dsl.selectDistinct(INV_SNAPSHOTS.OWNER)
                .from(INV_SNAPSHOTS)
                .fetch(row -> UUID.fromString(row.get(INV_SNAPSHOTS.OWNER))));
    }

    @Override
    public Optional<Snapshot> find(SnapshotId id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.selectFrom(INV_SNAPSHOTS)
                .where(INV_SNAPSHOTS.ID.eq(id.asText()))
                .fetchOptional()
                .map(SnapshotRows::toSnapshot));
    }

    @Override
    public void delete(SnapshotId id) {
        Objects.requireNonNull(id, "id");
        write(dsl -> {
            dsl.deleteFrom(INV_SNAPSHOTS)
                    .where(INV_SNAPSHOTS.ID.eq(id.asText()))
                    .execute();
            return null;
        });
    }

    @Override
    public int deleteBeyondCount(UUID owner, int keep) {
        Objects.requireNonNull(owner, "owner");
        if (keep < 0) {
            throw new IllegalArgumentException("keep must not be negative: " + keep);
        }
        return write(dsl -> {
            List<String> ids = dsl.select(INV_SNAPSHOTS.ID)
                    .from(INV_SNAPSHOTS)
                    .where(INV_SNAPSHOTS.OWNER.eq(owner.toString()))
                    .orderBy(INV_SNAPSHOTS.CREATED_AT.desc(), INV_SNAPSHOTS.ID.desc())
                    .fetch(INV_SNAPSHOTS.ID);
            if (ids.size() <= keep) {
                return 0;
            }
            List<String> stale = List.copyOf(ids.subList(keep, ids.size()));
            return dsl.deleteFrom(INV_SNAPSHOTS)
                    .where(INV_SNAPSHOTS.ID.in(stale))
                    .execute();
        });
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return write(dsl -> dsl.deleteFrom(INV_SNAPSHOTS)
                .where(INV_SNAPSHOTS.CREATED_AT.lt(cutoff.toEpochMilli()))
                .execute());
    }
}
