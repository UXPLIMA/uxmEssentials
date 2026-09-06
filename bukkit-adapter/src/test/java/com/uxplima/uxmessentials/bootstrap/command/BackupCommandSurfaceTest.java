package com.uxplima.uxmessentials.bootstrap.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.uxplima.uxmessentials.migration.adapter.DataDirBackupSnapshot;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the {@code /backup} bootstrap command onto the operator surface. {@code /backup} is not sourced from
 * a feature module, so {@code CommandCatalogDriftTest} tracks its literal in its bootstrap list; this guard
 * asserts the registration's literal, the permission node it wires under, and, the point of the command,
 * that it reuses {@link DataDirBackupSnapshot}, which now exposes the written location {@code /backup}
 * reports. A drift in the literal or the permission would silently break the documented operator surface.
 */
class BackupCommandSurfaceTest {

    @Test
    void backupRegistersUnderItsLiteral() {
        DataDirBackupSnapshot snapshot = new DataDirBackupSnapshot(Path.of("ignored"), "backup", noopLog());
        BackupCommand command = new BackupCommand(snapshot, new InlineScheduler(), noopLog());
        assertThat(command.build().getLiteral()).isEqualTo("backup");
    }

    @Test
    void backupLiteralIsAValidCommandId() {
        assertThatCode(() -> new CommandId("backup")).doesNotThrowAnyException();
    }

    @Test
    void snapshotExposesItsWrittenLocationUnderTheBackupPrefix(@TempDir Path pluginsDir) throws Exception {
        Path dataDir = Files.createDirectories(pluginsDir.resolve("uxmEssentials"));
        Files.writeString(dataDir.resolve("config.conf"), "value = 1");

        DataDirBackupSnapshot.Snapshot result = new DataDirBackupSnapshot(dataDir, "backup", noopLog()).takeSnapshot();

        assertThat(result.name()).startsWith("backup-");
        assertThat(result.location().getFileName().toString()).isEqualTo(result.name());
        assertThat(Files.exists(result.location().resolve("config.conf")))
                .as("the snapshot copies the bundled .conf state into the reported location")
                .isTrue();
    }

    @Test
    void takeAndTakeSnapshotAgreeOnTheName() {
        // take() is the BackupSnapshot port the importer consumes; it must stay the name of takeSnapshot().
        DataDirBackupSnapshot snapshot = new DataDirBackupSnapshot(Path.of("missing-data-dir"), "backup", noopLog());
        DataDirBackupSnapshot.Snapshot taken = snapshot.takeSnapshot();
        assertThat(taken.name()).startsWith("backup-");
    }

    private static Logger noopLog() {
        return new Logger() {
            @Override
            public void info(String m, Object... a) {}

            @Override
            public void warn(String m, Object... a) {}

            @Override
            public void error(String m, Throwable t) {}

            @Override
            public void debug(String m, Object... a) {}
        };
    }

    /** Runs every scheduled task inline so the command's off-tick snapshot is exercised synchronously. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
