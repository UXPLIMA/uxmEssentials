package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.jspecify.annotations.NullMarked;

/**
 * Announces this backend's presence to the cluster on a bounded interval. Each tick publishes a fresh
 * {@link ServerPing} stamped with this backend's {@code server-id} and the current wall-clock time; peers
 * record it into their {@link ClusterPeers} roster so the cluster-peer line in {@code /uxmess doctor} stays
 * current. A backend that stops heartbeating ages out of every peer's roster once its last ping leaves the
 * liveness window.
 *
 * <p>It uses the same self-rescheduling loop as the salary and loan-repayment sweeps, schedule the next tick
 * through {@link Scheduler#asyncAfter} rather than holding a raw repeating handle, so a {@code stop()} simply
 * flips the running flag and the in-flight loop drops out on its next check, leaving no orphaned task. The
 * publish is pure bus work (encode plus a fire-and-forget transport send) and touches no Bukkit API, so it
 * runs straight on the async scheduler.
 *
 * <p>The heartbeat only exists for an enabled backend: it is created and started from the live bus lifecycle,
 * which a disabled backend never builds, so a {@code Bus.disabled} backend publishes nothing.
 */
@NullMarked
public final class ClusterHeartbeat {

    private final BusPublisher publisher;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration interval;
    private final LongSupplier clock;
    private volatile boolean running;

    /** Heartbeat publishing through {@code publisher} every {@code interval}, reading the wall clock for each stamp. */
    public ClusterHeartbeat(BusPublisher publisher, Scheduler scheduler, Duration interval, Logger log) {
        this(publisher, scheduler, interval, log, System::currentTimeMillis);
    }

    ClusterHeartbeat(BusPublisher publisher, Scheduler scheduler, Duration interval, Logger log, LongSupplier clock) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("heartbeat interval must be positive: " + interval);
        }
    }

    /** Begin heartbeating; the first ping fires after one interval so peers are not pinged before they wire. */
    public void start() {
        running = true;
        scheduleNext();
    }

    /** Stop heartbeating; the in-flight loop drops out on its next tick. Idempotent. */
    public void stop() {
        running = false;
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        scheduler.asyncAfter(interval, this::tick);
    }

    private void tick() {
        if (!running) {
            return;
        }
        try {
            publisher.publish(new ServerPing(publisher.serverId(), clock.getAsLong()));
        } catch (RuntimeException failure) {
            // A throwing publish must not skip the reschedule below, or this backend would drop off every peer's
            // roster.
            log.error("cluster heartbeat publish failed", failure);
        }
        scheduleNext();
    }
}
