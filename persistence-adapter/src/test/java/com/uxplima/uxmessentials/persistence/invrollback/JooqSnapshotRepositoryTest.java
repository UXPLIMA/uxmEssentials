package com.uxplima.uxmessentials.persistence.invrollback;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqSnapshotRepository} against the default embedded SQLite backend with the
 * Flyway V79 {@code inv_snapshots} table applied. It proves the round-trip (save → list/find), that the opaque
 * serialized contents survive the base64 TEXT column byte-for-byte, that the cause and capture instant persist,
 * that the listing is newest-first, that an empty capture stores a null cell and round-trips to empty, that a
 * delete removes exactly one row (idempotent on a missing id), that {@code deleteBeyondCount} keeps the newest N,
 * and that {@code deleteOlderThan} removes rows strictly before the cutoff.
 */
class JooqSnapshotRepositoryTest {

    private Persistence persistence;
    private JooqSnapshotRepository repository;
    private UUID owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqSnapshotRepository(persistence.dsl());
        owner = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndListsSnapshotsNewestFirstRoundTrippingContentsAndCause() {
        byte[] older = {0, 1, 2, 3};
        byte[] newer = {(byte) 200, (byte) 255, 7};
        repository.save(snapshot(SnapshotCause.LOGOUT, Instant.ofEpochMilli(1_000), older));
        repository.save(snapshot(SnapshotCause.DEATH, Instant.ofEpochMilli(2_000), newer));

        List<Snapshot> listed = repository.list(owner);

        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).cause()).isEqualTo(SnapshotCause.DEATH);
        assertThat(listed.get(0).createdAt()).isEqualTo(Instant.ofEpochMilli(2_000));
        assertThat(listed.get(0).contents()).containsExactly(newer);
        assertThat(listed.get(1).cause()).isEqualTo(SnapshotCause.LOGOUT);
        assertThat(listed.get(1).contents()).containsExactly(older);
    }

    @Test
    void findResolvesAStoredSnapshotAndIsEmptyForAnUnknownId() {
        Snapshot saved = snapshot(SnapshotCause.DEATH, Instant.ofEpochMilli(5_000), new byte[] {9, 9});
        repository.save(saved);

        Snapshot found = repository.find(saved.id()).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.contents()).containsExactly(9, 9);
        assertThat(repository.find(SnapshotId.random())).isEmpty();
    }

    @Test
    void anEmptyCaptureStoresNoBlobAndRoundTripsToEmpty() {
        Snapshot saved = snapshot(SnapshotCause.LOGOUT, Instant.ofEpochMilli(1_000), new byte[0]);
        repository.save(saved);

        Snapshot found = repository.find(saved.id()).orElseThrow();

        assertThat(found.contents()).isEmpty();
    }

    @Test
    void deleteRemovesExactlyTheOneRowAndIsIdempotent() {
        Snapshot a = snapshot(SnapshotCause.DEATH, Instant.ofEpochMilli(1_000), new byte[] {1});
        Snapshot b = snapshot(SnapshotCause.DEATH, Instant.ofEpochMilli(2_000), new byte[] {2});
        repository.save(a);
        repository.save(b);

        repository.delete(a.id());
        repository.delete(SnapshotId.random()); // no such row, a silent no-op

        assertThat(repository.list(owner)).extracting(Snapshot::id).containsExactly(b.id());
    }

    @Test
    void deleteBeyondCountKeepsTheNewestNAndReturnsTheRemovedCount() {
        for (int i = 1; i <= 5; i++) {
            repository.save(snapshot(SnapshotCause.LOGOUT, Instant.ofEpochMilli(i * 1_000L), new byte[] {(byte) i}));
        }

        int removed = repository.deleteBeyondCount(owner, 2);

        assertThat(removed).isEqualTo(3);
        List<Snapshot> kept = repository.list(owner);
        assertThat(kept)
                .extracting(Snapshot::createdAt)
                .containsExactly(Instant.ofEpochMilli(5_000), Instant.ofEpochMilli(4_000));
    }

    @Test
    void deleteBeyondCountRemovesNothingWhenUnderTheCap() {
        repository.save(snapshot(SnapshotCause.DEATH, Instant.ofEpochMilli(1_000), new byte[] {1}));

        assertThat(repository.deleteBeyondCount(owner, 5)).isZero();
        assertThat(repository.list(owner)).hasSize(1);
    }

    @Test
    void ownersReturnsEachDistinctOwnerThatHoldsSnapshots() {
        UUID other = UUID.randomUUID();
        repository.save(snapshot(SnapshotCause.DEATH, Instant.ofEpochMilli(1_000), new byte[] {1}));
        repository.save(snapshot(SnapshotCause.LOGOUT, Instant.ofEpochMilli(2_000), new byte[] {2}));
        repository.save(
                Snapshot.of(SnapshotId.random(), other, SnapshotCause.DEATH, Instant.ofEpochMilli(3_000), new byte[0]));

        assertThat(repository.owners()).containsExactlyInAnyOrder(owner, other);
    }

    @Test
    void deleteOlderThanRemovesRowsStrictlyBeforeTheCutoff() {
        repository.save(snapshot(SnapshotCause.LOGOUT, Instant.ofEpochMilli(1_000), new byte[] {1}));
        repository.save(snapshot(SnapshotCause.LOGOUT, Instant.ofEpochMilli(2_000), new byte[] {2}));
        repository.save(snapshot(SnapshotCause.LOGOUT, Instant.ofEpochMilli(3_000), new byte[] {3}));

        int removed = repository.deleteOlderThan(Instant.ofEpochMilli(2_000));

        assertThat(removed).isEqualTo(1); // only the 1_000 row is strictly before the cutoff
        assertThat(repository.list(owner))
                .extracting(Snapshot::createdAt)
                .containsExactly(Instant.ofEpochMilli(3_000), Instant.ofEpochMilli(2_000));
    }

    private Snapshot snapshot(SnapshotCause cause, Instant createdAt, byte[] contents) {
        return Snapshot.of(SnapshotId.random(), owner, cause, createdAt, contents);
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
