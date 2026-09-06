package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the per-hologram text refresh from a single global timer. The timer ticks at a fixed
 * {@link #baseTicks} cadence (the smallest interval a hologram may refresh at); on each tick it advances a
 * running tick count and re-renders only the holograms whose {@link Hologram#refreshIntervalTicks()} has
 * elapsed. Static holograms (interval 0) and holograms whose interval has not come due cost nothing beyond the
 * iteration, so a server with no refreshing hologram pays only the empty loop.
 *
 * <p>The class is deliberately free of any scheduler reference: it exposes one {@link #tick()} method the
 * wiring schedules through the kernel {@code Scheduler.repeatGlobal}, which is also what makes it unit-testable
 * with a fake clock. Re-rendering goes through a {@code refresh} callback (the renderer's {@code refresh}),
 * which only re-renders a hologram it is still tracking, so a hologram deleted between ticks is skipped.
 */
@NullMarked
public final class HologramRefreshTask {

    private final Supplier<List<Hologram>> holograms;
    private final Consumer<Hologram> refresh;
    private final Logger log;
    private final int baseTicks;
    private long elapsedTicks;

    public HologramRefreshTask(
            Supplier<List<Hologram>> holograms, Consumer<Hologram> refresh, Logger log, int baseTicks) {
        this.holograms = Objects.requireNonNull(holograms, "holograms");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.log = Objects.requireNonNull(log, "log");
        if (baseTicks <= 0) {
            throw new IllegalArgumentException("baseTicks must be positive: " + baseTicks);
        }
        this.baseTicks = baseTicks;
    }

    /** Advance the timer by one base period and re-render every hologram whose interval has come due. */
    public void tick() {
        elapsedTicks += baseTicks;
        for (Hologram hologram : holograms.get()) {
            if (isDue(hologram)) {
                refreshOne(hologram);
            }
        }
    }

    private void refreshOne(Hologram hologram) {
        try {
            refresh.accept(hologram);
        } catch (RuntimeException failure) {
            // One hologram that throws on refresh must not abort the rest of this tick's due holograms.
            log.error("hologram refresh failed", failure);
        }
    }

    private boolean isDue(Hologram hologram) {
        if (!hologram.refreshes()) {
            return false;
        }
        return elapsedTicks % effectiveInterval(hologram.refreshIntervalTicks()) == 0;
    }

    private int effectiveInterval(int interval) {
        // Round a sub-base interval up to the base cadence: the timer cannot fire more often than baseTicks.
        return Math.max(baseTicks, interval - (interval % baseTicks));
    }
}
