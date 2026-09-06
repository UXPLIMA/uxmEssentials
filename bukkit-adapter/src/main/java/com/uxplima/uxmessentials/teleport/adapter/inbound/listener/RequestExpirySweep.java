package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryRequestRegistry;
import com.uxplima.uxmessentials.teleport.application.AcceptTeleport;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code tpa} TTL expiry sweep: a self-rescheduling async loop that walks the in-flight requests and
 * expires each one whose TTL has elapsed, notifying both parties through {@link AcceptTeleport}. It runs
 * off the tick thread (the expiry decision is pure; the player-facing notice hops to the viewer's region
 * inside the message sink) and observes the module's {@code running} flag so it stops cleanly on disable.
 *
 * <p>The sweep is the only driver of expiry, the registry never expires lazily, so a request that no
 * one resolves is cleaned up within one sweep interval and both parties get the expiry message.
 */
@NullMarked
public final class RequestExpirySweep {

    private static final Duration INTERVAL = Duration.ofSeconds(1);

    private final Scheduler scheduler;
    private final InMemoryRequestRegistry requests;
    private final AcceptTeleport acceptTeleport;
    private final Logger log;
    private final BooleanSupplier running;

    public RequestExpirySweep(
            Scheduler scheduler,
            InMemoryRequestRegistry requests,
            AcceptTeleport acceptTeleport,
            Logger log,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.acceptTeleport = Objects.requireNonNull(acceptTeleport, "acceptTeleport");
        this.log = Objects.requireNonNull(log, "log");
        this.running = Objects.requireNonNull(running, "running");
    }

    /** Arm the first sweep; subsequent sweeps reschedule themselves until the module stops. */
    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.asyncAfter(INTERVAL, this::sweepOnce);
    }

    private void sweepOnce() {
        if (!running.getAsBoolean()) {
            return;
        }
        try {
            requests.snapshot().forEach(acceptTeleport::expireIfDue);
        } catch (RuntimeException failure) {
            // A throwing expiry must not skip the reschedule below, or stale /tpa requests would never clear.
            log.error("tpa request-expiry sweep failed", failure);
        }
        scheduleNext();
    }
}
