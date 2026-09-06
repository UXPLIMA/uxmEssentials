package com.uxplima.uxmessentials.migration;

import java.time.Duration;

import com.uxplima.uxmessentials.migration.convert.SourceId;
import org.jspecify.annotations.NullMarked;

/**
 * The importer's audit seam: every run emits a {@code migration_import_start}, one
 * {@code migration_import_*} line per record, and a {@code migration_import_finish} summary
 * (docs/12-migration §9). The {@code start → per-record → finish} family makes any run, including a dry
 * run, which emits the identical lines with {@code dry_run=true}, fully replayable from the audit log.
 * The bukkit-side impl routes these to the {@code com.uxplima.uxmessentials.audit} channel;
 * {@code AuditEventsDocsDriftTest} pins every emit-site to a documented event row.
 */
@NullMarked
public interface MigrationAudit {

    /** {@code event=migration_import_start}, actor, source, dry-run, source path, pre-import backup name. */
    void start(String actor, SourceId source, ImportMode mode, String sourcePath, String backup);

    /** {@code event=migration_import_<kind>}: one line per record, carrying the conflict branch and ok flag. */
    void record(SourceId source, ImportRecord rec, RecordOutcome outcome);

    /** {@code event=migration_import_finish}, the summary tally plus the run duration. */
    void finish(String actor, ImportSummary summary, Duration elapsed);
}
