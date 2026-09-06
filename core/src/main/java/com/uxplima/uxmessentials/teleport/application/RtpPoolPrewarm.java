package com.uxplima.uxmessentials.teleport.application;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;

/**
 * The pre-warm half of the durable RTP pool: on module enable it re-populates a world's in-memory queue from the
 * persisted columns, so the first {@code /rtp} after a restart serves instantly instead of triggering a cold
 * search storm. It loads up to {@code limit} columns for the world off the tick thread, then re-probes each one
 * through the {@link AsyncSafeLocationFinder}. A persisted column is trusted only as a horizontal coordinate, so
 * the finder resolves a fresh landing Y and re-runs the full safety policy before the column re-enters the queue.
 * A known-good column re-probes fast with a high hit rate (one async chunk load that almost always passes), which
 * is the whole point: it replaces the cold 40-attempt search the first player would otherwise pay for.
 *
 * <p>Nothing here blocks or storms. The load is dispatched through the {@link Scheduler} so the synchronous
 * relational read never runs on a region thread, and the re-probes are run one at a time, spaced a couple of ticks
 * apart through the same scheduler, the P1 budget/slicing discipline, so pre-warm itself never fires N chunk
 * loads at once. Each re-validated location is handed to the {@code sink} (which offers it into the queue); a
 * column the world has changed under resolves to empty and is silently dropped, never served stale.
 */
public final class RtpPoolPrewarm {

    private final RtpPoolStore store;
    private final AsyncSafeLocationFinder finder;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration reprobeInterval;

    public RtpPoolPrewarm(
            RtpPoolStore store,
            AsyncSafeLocationFinder finder,
            Scheduler scheduler,
            Logger log,
            Duration reprobeInterval) {
        this.store = Objects.requireNonNull(store, "store");
        this.finder = Objects.requireNonNull(finder, "finder");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.reprobeInterval = Objects.requireNonNull(reprobeInterval, "reprobeInterval");
    }

    /**
     * Load up to {@code limit} persisted columns for {@code area}'s world and re-probe each, offering every
     * re-validated location to {@code sink}. Returns immediately; the load and the tick-sliced re-probes run
     * off-tick. A non-positive {@code limit} pre-warms nothing.
     */
    public void prewarm(SafeSearchArea area, int limit, Consumer<RtpSafeLocation> sink) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(sink, "sink");
        if (limit <= 0) {
            return;
        }
        scheduler.async(() -> loadAndReprobe(area, limit, sink));
    }

    private void loadAndReprobe(SafeSearchArea area, int limit, Consumer<RtpSafeLocation> sink) {
        List<RtpColumn> columns;
        try {
            columns = store.load(area.world(), limit);
        } catch (RuntimeException failure) {
            log.warn(
                    "rtp pool pre-warm load failed for world {}: {}",
                    area.world().name(),
                    String.valueOf(failure.getMessage()));
            return;
        }
        reprobeNext(area, new ArrayDeque<>(columns), sink);
    }

    private void reprobeNext(SafeSearchArea area, Deque<RtpColumn> pending, Consumer<RtpSafeLocation> sink) {
        RtpColumn column = pending.poll();
        if (column == null) {
            return;
        }
        // The finder never completes exceptionally (the ChunkAccess port always completes and the pure policy never
        // throws), so thenAccept always runs; a re-validated column is offered, a stale one dropped, then the next
        // column is re-probed a couple of ticks later so pre-warm never fires every chunk load at once.
        var ignored = finder.probeColumn(area, column.x(), column.z())
                .thenAccept(found -> onReprobed(area, pending, sink, found));
    }

    private void onReprobed(
            SafeSearchArea area,
            Deque<RtpColumn> pending,
            Consumer<RtpSafeLocation> sink,
            Optional<RtpSafeLocation> found) {
        found.ifPresent(sink);
        if (!pending.isEmpty()) {
            scheduler.asyncAfter(reprobeInterval, () -> reprobeNext(area, pending, sink));
        }
    }
}
