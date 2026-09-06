package com.uxplima.uxmessentials.migration;

/**
 * Whether an import writes to the database or only reports what it would write.
 *
 * <p>{@link #DRY_RUN} runs the entire pipeline (parse, map, conflict-resolve) and replaces only the
 * final write step with a no-op accumulator, so it mutates nothing while emitting the same per-record
 * and summary audit a {@link #LIVE} run would. The two modes share one code path; only the
 * {@code RecordWriter} differs.
 */
public enum ImportMode {

    /** A real import: records are written through the target contexts' application services. */
    LIVE,

    /** The full pipeline with the write replaced by a no-op accumulator; mutates nothing. */
    DRY_RUN;

    /** True for {@link #DRY_RUN}, the {@code dry_run} field on every audit line. */
    public boolean isDryRun() {
        return this == DRY_RUN;
    }
}
