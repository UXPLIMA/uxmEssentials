package com.uxplima.uxmessentials.migration;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.JailMuteMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXJail;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXModeration;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXMute;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The importer's idempotency and dry-run guarantees for the moderation path (docs/12-migration §1, §6).
 * A jailed-and-muted player streams as one {@code ModerationRecord}; a live run writes it once and a
 * re-run upserts (set, never append) so two runs leave exactly one stored sanction per uuid. A dry run
 * takes no backup, stores nothing, and still tallies the same {@code jails}/{@code mutes} summary so an
 * operator can review counts before committing. Pure application code, no Bukkit, no disk.
 */
class MigrationIdempotencyDryRunTest {

    private static final UUID GRIEFER = new UUID(0L, 3L);

    private final RecordingAudit audit = new RecordingAudit();
    private final CountingBackup backup = new CountingBackup();
    private final ImportData importData = new ImportData(audit, backup, NoOpLogger.INSTANCE);

    @Test
    void aModerationRecordIsTalliedAsAJailAndAMute() {
        SanctionSetWriter writer = new SanctionSetWriter();

        ImportSummary summary = importData.run("admin", source(), ImportOptions.live(Path.of(".")), writer);

        assertThat(summary.jails()).isEqualTo(1);
        assertThat(summary.mutes()).isEqualTo(1);
        assertThat(summary.total()).isEqualTo(1);
        assertThat(audit.records.get()).isEqualTo(1);
        assertThat(backup.taken.get()).isEqualTo(1);
    }

    @Test
    void reRunningLeavesExactlyOneStoredSanctionPerUuid() {
        SanctionSetWriter writer = new SanctionSetWriter();

        importData.run("admin", source(), ImportOptions.live(Path.of(".")), writer);
        importData.run("admin", source(), ImportOptions.live(Path.of(".")), writer);

        assertThat(writer.stored).containsOnlyKeys(GRIEFER);
        assertThat(writer.stored).hasSize(1);
    }

    @Test
    void aDryRunTakesNoBackupWritesNothingButTalliesTheSameSummary() {
        SanctionSetWriter writer = new SanctionSetWriter();
        ImportOptions dryRun = ImportOptions.live(Path.of(".")).asDryRun();

        ImportSummary summary = importData.run("admin", source(), dryRun, writer);

        assertThat(backup.taken.get()).isZero();
        assertThat(writer.stored).isEmpty();
        assertThat(summary.dryRun()).isTrue();
        assertThat(summary.jails()).isEqualTo(1);
        assertThat(summary.mutes()).isEqualTo(1);
    }

    private static Convert source() {
        return new FakeModerationSource();
    }

    /** A fake source streaming one jailed-and-muted player, mapped through the real {@link JailMuteMapper}. */
    private record FakeModerationSource() implements Convert {
        @Override
        public SourceId id() {
            return SourceId.of("essentialsx");
        }

        @Override
        public SourceDescriptor describe() {
            return new SourceDescriptor(id(), "Fake", "in-memory", List.of("moderation"));
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
                    return Stream.of(moderationRecord());
                }

                @Override
                public void close() {}
            };
        }

        private static ImportRecord moderationRecord() {
            PlayerRef owner = new PlayerRef(GRIEFER, "griefer");
            EssXModeration src = new EssXModeration(
                    Optional.of(new EssXMute(Optional.of("spam"), Optional.empty())),
                    Optional.of(new EssXJail("cell", Optional.of(1_700_200_000_000L), Optional.empty())));
            ImportedModeration mapped = new JailMuteMapper().map(owner, src).orElseThrow();
            return new ImportRecord.ModerationRecord(mapped);
        }
    }

    /** A writer that "sets" each sanctioned uuid into a map: a re-run replaces, never duplicates. */
    private static final class SanctionSetWriter implements RecordWriter {
        private final ConcurrentHashMap<UUID, Boolean> stored = new ConcurrentHashMap<>();

        @Override
        public RecordOutcome write(ImportRecord record, ImportOptions options) {
            if (record instanceof ImportRecord.ModerationRecord rec && !options.isDryRun()) {
                stored.put(rec.moderation().profile().target().uuid(), Boolean.TRUE);
            }
            return RecordOutcome.WRITTEN;
        }
    }

    private static final class RecordingAudit implements MigrationAudit {
        private final AtomicInteger records = new AtomicInteger();

        @Override
        public void start(String actor, SourceId source, ImportMode mode, String sourcePath, String backup) {}

        @Override
        public void record(SourceId source, ImportRecord rec, RecordOutcome outcome) {
            records.incrementAndGet();
        }

        @Override
        public void finish(String actor, ImportSummary summary, Duration elapsed) {}
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
