package com.uxplima.uxmessentials.migration;

import org.jspecify.annotations.NullMarked;

/**
 * The write seam: applies one mapped {@link ImportRecord} to the target contexts' application services
 * under the run's {@link ImportOptions}, honouring the conflict and balance policies, and reports the
 * {@link RecordOutcome}. A live run binds the real writer (which calls {@code SetHome}/{@code SetWarp}/
 * economy services through the same ports a live command uses, so an import can never create a state a
 * normal command could not); a dry-run binds the no-op accumulator, which runs the identical
 * conflict-resolution logic and reports the same outcomes without touching the database
 * (docs/12-migration §1, §7).
 *
 * <p>This port is the <em>only</em> difference between a live run and a dry run, the parse, map, and
 * conflict-resolve stages are shared, so a dry run's per-record and summary audit are byte-for-byte the
 * lines a live run would emit (modulo {@code dry_run=true}).
 */
@NullMarked
@FunctionalInterface
public interface RecordWriter {

    /** Apply one record under {@code options} and report what happened. Never throws for a bad record. */
    RecordOutcome write(ImportRecord record, ImportOptions options);
}
