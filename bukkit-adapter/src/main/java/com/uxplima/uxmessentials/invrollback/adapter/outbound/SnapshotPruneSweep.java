package com.uxplima.uxmessentials.invrollback.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.invrollback.application.PruneSnapshots;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The scheduled half of retention: a repeating tick that applies the {@link PruneSnapshots} sweep so
 * {@code inv_snapshots} stays bounded by age (and by count for any owner left over the cap after a reload). The
 * repeating tick fires on the global region thread through the injected {@link Scheduler}, and immediately hands the
 * DB work to the async lane. A snapshot prune is a write, which must never run on a tick thread (SQLite is
 * single-writer, and no region thread may block on the DB).
 *
 * <p>The sweep is skipped entirely when both retention limits are disabled ({@code max-per-player} and
 * {@code max-age-days} both zero): {@link #start} returns a no-op handle and schedules nothing, so a disabled
 * retention policy strands no repeating task. The returned handle is closed on module stop to cancel the tick.
 */
@NullMarked
public final class SnapshotPruneSweep {

    /** The first sweep runs a few minutes after enable (so it never collides with startup), then hourly. */
    private static final Duration INITIAL_DELAY = Duration.ofMinutes(5);

    private static final Duration PERIOD = Duration.ofMinutes(60);

    private final PruneSnapshots pruneSnapshots;
    private final Scheduler scheduler;
    private final Clock clock;
    private final boolean enabled;

    /**
     * @param pruneSnapshots the retention sweep use case
     * @param scheduler the Folia-aware scheduler the repeating tick and the async DB hop go through
     * @param clock the reference clock the age cutoff is computed from
     * @param enabled whether either retention limit is set; when false the sweep never schedules
     */
    public SnapshotPruneSweep(PruneSnapshots pruneSnapshots, Scheduler scheduler, Clock clock, boolean enabled) {
        this.pruneSnapshots = Objects.requireNonNull(pruneSnapshots, "pruneSnapshots");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.enabled = enabled;
    }

    /** Start the repeating sweep, returning the handle the module closes on stop; a no-op when retention is off. */
    public AutoCloseable start() {
        if (!enabled) {
            return () -> {};
        }
        return scheduler.repeatGlobal(this::tick, INITIAL_DELAY, PERIOD);
    }

    /** One sweep pass: hand the retention prune to the async lane so the DB writes never touch a tick thread. */
    void tick() {
        scheduler.async(() -> pruneSnapshots.sweep(clock.instant()));
    }
}
