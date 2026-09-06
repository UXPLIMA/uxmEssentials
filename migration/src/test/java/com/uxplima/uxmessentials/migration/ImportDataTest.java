package com.uxplima.uxmessentials.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Exercises the source-agnostic {@link ImportData} use case end to end with an in-process fake source and
 * a counting writer: a live run emits {@code start → per-record → finish} audit and writes once; a re-run
 * with the same records is idempotent at the writer (set-not-add); a dry run takes no backup and still
 * produces the identical summary. No Bukkit, no disk: the use case is pure application code.
 */
class ImportDataTest {

    private final RecordingAudit audit = new RecordingAudit();
    private final CountingBackup backup = new CountingBackup();
    private final ImportData importData = new ImportData(audit, backup, NoOpLogger.INSTANCE);

    @Test
    void liveRunTakesABackupEmitsAuditAndWritesEveryRecord() {
        SetWriter writer = new SetWriter();
        ImportSummary summary = importData.run("admin", source(3), ImportOptions.live(Path.of(".")), writer);

        assertThat(backup.taken.get()).isEqualTo(1);
        assertThat(summary.users()).isEqualTo(3);
        assertThat(summary.total()).isEqualTo(3);
        assertThat(writer.writes.get()).isEqualTo(3);
        assertThat(audit.started).isTrue();
        assertThat(audit.finished).isTrue();
        assertThat(audit.records.get()).isEqualTo(3);
    }

    @Test
    void reRunIsIdempotentAtTheWriter() {
        SetWriter writer = new SetWriter();
        importData.run("admin", source(2), ImportOptions.live(Path.of(".")), writer);
        importData.run("admin", source(2), ImportOptions.live(Path.of(".")), writer);

        // Two runs of the same two users leave exactly two stored owners, the set-not-add upsert.
        assertThat(writer.stored).hasSize(2);
    }

    @Test
    void dryRunTakesNoBackupButProducesTheSameSummaryShape() {
        SetWriter writer = new SetWriter();
        ImportOptions dryRun = ImportOptions.live(Path.of(".")).asDryRun();
        ImportSummary summary = importData.run("admin", source(2), dryRun, writer);

        assertThat(backup.taken.get()).isZero();
        assertThat(summary.dryRun()).isTrue();
        assertThat(summary.users()).isEqualTo(2);
        assertThat(audit.records.get()).isEqualTo(2);
    }

    private static Convert source(int users) {
        return new FakeSource(users);
    }

    /** A fake source that streams {@code n} user records, each a distinct uuid with a fixed balance. */
    private record FakeSource(int users) implements Convert {
        @Override
        public SourceId id() {
            return SourceId.of("essentialsx");
        }

        @Override
        public SourceDescriptor describe() {
            return new SourceDescriptor(id(), "Fake", "in-memory", List.of("homes"));
        }

        @Override
        public boolean detect(Path sourcePath) {
            return true;
        }

        @Override
        public ImportPlan plan(ImportOptions options) {
            return new ImportPlan() {
                @Override
                public Stream<ImportRecord> records() {
                    return Stream.iterate(0, i -> i + 1).limit(users).map(FakeSource::userRecord);
                }

                @Override
                public void close() {}
            };
        }

        private static ImportRecord userRecord(int index) {
            PlayerRef owner = new PlayerRef(new UUID(0L, index), "p" + index);
            ImportedUser user = new ImportedUser(owner, List.of(), Optional.of(new BigDecimal("10.00")), List.of());
            return new ImportRecord.UserRecord(user);
        }
    }

    /** A writer that "sets" each owner into a map: a re-run replaces, never duplicates. */
    private static final class SetWriter implements RecordWriter {
        private final AtomicInteger writes = new AtomicInteger();
        private final ConcurrentHashMap<UUID, Boolean> stored = new ConcurrentHashMap<>();

        @Override
        public RecordOutcome write(ImportRecord record, ImportOptions options) {
            writes.incrementAndGet();
            if (record instanceof ImportRecord.UserRecord user) {
                stored.put(user.user().owner().uuid(), Boolean.TRUE);
            }
            return RecordOutcome.WRITTEN;
        }
    }

    private static final class RecordingAudit implements MigrationAudit {
        private volatile boolean started;
        private volatile boolean finished;
        private final AtomicInteger records = new AtomicInteger();

        @Override
        public void start(String actor, SourceId source, ImportMode mode, String sourcePath, String backup) {
            started = true;
        }

        @Override
        public void record(SourceId source, ImportRecord rec, RecordOutcome outcome) {
            records.incrementAndGet();
        }

        @Override
        public void finish(String actor, ImportSummary summary, Duration elapsed) {
            finished = true;
        }
    }

    private static final class CountingBackup implements BackupSnapshot {
        private final AtomicInteger taken = new AtomicInteger();

        @Override
        public String take() {
            taken.incrementAndGet();
            return "snapshot";
        }
    }

    private enum NoOpLogger implements Logger {
        INSTANCE;

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
