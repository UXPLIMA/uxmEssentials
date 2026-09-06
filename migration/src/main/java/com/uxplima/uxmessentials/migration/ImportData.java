package com.uxplima.uxmessentials.migration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The source-agnostic import use case (docs/12-migration §1, §7). It is the single write path every
 * {@link Convert} source funnels through, so idempotency, conflict policy, backup-first, and per-record
 * audit are identical no matter which source ran:
 *
 * <ol>
 *   <li>Take a pre-import backup (live runs only) and emit {@code migration_import_start}.
 *   <li>Stream the source's mapped records, batch them, and write each batch off-tick on the bounded
 *       {@link ImportExecutor} through the bound {@link RecordWriter} (the real writer for a live run,
 *       the no-op accumulator for a dry run, the only difference between the two).
 *   <li>Emit one {@code migration_import_<kind>} per record and fold its outcome into the summary.
 *   <li>Await quiescence and emit {@code migration_import_finish}.
 * </ol>
 *
 * <p>Every write is an upsert keyed by stable identity, so a re-run is idempotent and resumable; this use
 * case never performs an additive balance mutation (§6). It runs entirely off the tick thread, the
 * command handler dispatches it and returns immediately ({@code MainThreadBlockingDriftTest}).
 */
@NullMarked
public final class ImportData {

    private static final long DRAIN_TIMEOUT_MINUTES = 10L;

    private final MigrationAudit audit;
    private final BackupSnapshot backup;
    private final Logger log;

    public ImportData(MigrationAudit audit, BackupSnapshot backup, Logger log) {
        this.audit = Objects.requireNonNull(audit, "audit");
        this.backup = Objects.requireNonNull(backup, "backup");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Run one import end to end and return its summary. Caller supplies the source, options, and writer. */
    public ImportSummary run(String actor, Convert source, ImportOptions options, RecordWriter writer) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(writer, "writer");
        String snapshot = options.isDryRun() ? "dry-run" : backup.take();
        audit.start(actor, source.id(), options.mode(), options.sourcePath().toString(), snapshot);
        ImportSummary summary = new ImportSummary(source.id(), options.mode());
        Duration elapsed = drain(source, options, writer, summary);
        audit.finish(actor, summary, elapsed);
        return summary;
    }

    private Duration drain(Convert source, ImportOptions options, RecordWriter writer, ImportSummary summary) {
        long startedAt = System.nanoTime();
        Run run = new Run(source.id(), options, writer, summary);
        try (ImportExecutor executor = new ImportExecutor();
                ImportPlan plan = source.plan(options)) {
            dispatchBatches(plan.records(), executor, run);
            awaitDrain(executor);
        }
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private void dispatchBatches(Stream<ImportRecord> records, ImportExecutor executor, Run run) {
        List<ImportRecord> batch = new ArrayList<>(ImportExecutor.BATCH);
        for (ImportRecord rec : (Iterable<ImportRecord>) records::iterator) {
            batch.add(rec);
            if (batch.size() >= ImportExecutor.BATCH) {
                submit(executor, batch, run);
                batch = new ArrayList<>(ImportExecutor.BATCH);
            }
        }
        if (!batch.isEmpty()) {
            submit(executor, batch, run);
        }
    }

    private void submit(ImportExecutor executor, List<ImportRecord> batch, Run run) {
        executor.submitBatch(batch, written -> writeBatch(written, run));
    }

    private void writeBatch(List<ImportRecord> batch, Run run) {
        for (ImportRecord rec : batch) {
            RecordOutcome outcome = writeOne(rec, run);
            audit.record(run.source(), rec, outcome);
            run.summary().record(rec, outcome);
        }
    }

    private RecordOutcome writeOne(ImportRecord rec, Run run) {
        try {
            return run.writer().write(rec, run.options());
        } catch (RuntimeException badRecord) {
            // One record's failure is counted and the run continues (docs/12-migration §4).
            log.warn("migration record failed kind={} cause={}", rec.kind(), badRecord.toString());
            return RecordOutcome.FAILED;
        }
    }

    private void awaitDrain(ImportExecutor executor) {
        try {
            if (!executor.awaitQuiescence(DRAIN_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                log.warn("migration import did not drain within {} minutes", DRAIN_TIMEOUT_MINUTES);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("migration import drain interrupted");
        }
    }

    /** The immutable per-run context handed to each batch write so the closure stays allocation-light. */
    private record Run(SourceId source, ImportOptions options, RecordWriter writer, ImportSummary summary) {}
}
