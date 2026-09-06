package com.uxplima.uxmessentials.migration.adapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.BackupSnapshot;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Snapshots the plugin's config/data tree under a timestamped sibling directory. The migration importer
 * takes one before a live run writes anything (docs/12-migration §9, step 1), recording its name on the
 * {@code migration_import_start} audit line so a bad import is always recoverable to the pre-import config;
 * the {@code /backup} command takes one on demand. It is a config snapshot, not the off-host DB backup the
 * runbook also demands, that remains the operator's responsibility, but it captures the bundled
 * {@code .conf} state under the data directory.
 *
 * <p>The {@code prefix} names the snapshot directory ({@code pre-import-…} for the importer,
 * {@code backup-…} for {@code /backup}), so the two callers' snapshots sit side by side and self-describe.
 */
@NullMarked
public final class DataDirBackupSnapshot implements BackupSnapshot {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC);

    private final Path dataDir;
    private final String prefix;
    private final Logger log;

    public DataDirBackupSnapshot(Path dataDir, Logger log) {
        this(dataDir, "pre-import", log);
    }

    public DataDirBackupSnapshot(Path dataDir, String prefix, Logger log) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** A taken snapshot: its directory name (the audit {@code backup} field) and absolute location. */
    public record Snapshot(String name, Path location) {
        public Snapshot {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(location, "location");
        }
    }

    @Override
    public String take() {
        return takeSnapshot().name();
    }

    /** Take a snapshot and return both its name and the directory it was written to. */
    public Snapshot takeSnapshot() {
        String name = prefix + "-" + STAMP.format(Instant.now());
        Path target = dataDir.resolveSibling(dataDir.getFileName() + "-backups").resolve(name);
        try {
            copyConfigs(target);
            log.info("data snapshot written to {}", target);
            return new Snapshot(name, target);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to write data snapshot to " + target, failure);
        }
    }

    private void copyConfigs(Path target) throws IOException {
        Files.createDirectories(target);
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dataDir)) {
            for (Path file : files.filter(DataDirBackupSnapshot::isConf).toList()) {
                Files.copy(file, target.resolve(file.getFileName()));
            }
        }
    }

    private static boolean isConf(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().endsWith(".conf");
    }
}
