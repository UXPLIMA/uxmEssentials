package com.uxplima.uxmessentials.presence.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The auto-AFK sweep: a self-rescheduling async loop that reads the presence map and flips any player idle past
 * the configured threshold to AFK (docs/02-concurrency.md §6.9). It runs off the tick thread: the scan reads
 * immutable aggregates from the {@code ConcurrentHashMap} via {@link PresenceStore#snapshotAll}, never touching
 * a live {@code Player}, and observes the module's {@code running} flag so it stops cleanly on disable, the
 * §6.10 self-rescheduling-loop pattern matching the messaging mail-expiry sweep and the teleport request-expiry
 * sweep.
 *
 * <p>When the sweep decides a player is idle it does not mutate state inline: it hops to that player's owning
 * region/entity thread through the {@link Scheduler} port and runs {@link MarkAfk#markAuto} there, so the AFK
 * transition (and any visible side-effect the use case drives) happens on the thread that owns the entity, as
 * the concurrency doc requires. The sweep itself only reads; the write is region-local.
 *
 * <p>A non-positive idle threshold disables auto-AFK: {@link PlayerPresence#isIdlePast} returns false for every
 * player, so the sweep keeps running but flips no one. An operator switches auto-AFK off by setting the window
 * to zero. The first sweep is delayed by one interval so enable is not blocked.
 */
@NullMarked
public final class AfkSweep {

    private final Scheduler scheduler;
    private final PresenceStore store;
    private final MarkAfk markAfk;
    private final Logger log;
    private final Duration interval;
    private final Duration idleThreshold;
    private final BooleanSupplier running;
    private final Clock clock;

    public AfkSweep(
            Scheduler scheduler,
            PresenceStore store,
            MarkAfk markAfk,
            Logger log,
            Duration interval,
            Duration idleThreshold,
            BooleanSupplier running,
            Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.store = Objects.requireNonNull(store, "store");
        this.markAfk = Objects.requireNonNull(markAfk, "markAfk");
        this.log = Objects.requireNonNull(log, "log");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.idleThreshold = Objects.requireNonNull(idleThreshold, "idleThreshold");
        this.running = Objects.requireNonNull(running, "running");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Arm the first sweep; subsequent sweeps reschedule themselves until the module stops. */
    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.asyncAfter(interval, this::sweepOnce);
    }

    private void sweepOnce() {
        if (!running.getAsBoolean()) {
            return;
        }
        try {
            flipIdlePlayers();
        } catch (RuntimeException failure) {
            // A throwing scan must not skip the reschedule below, or auto-AFK would stop sweeping until a reload.
            log.error("auto-afk sweep failed", failure);
        }
        scheduleNext();
    }

    private void flipIdlePlayers() {
        if (idleThreshold.isZero() || idleThreshold.isNegative()) {
            return;
        }
        var now = clock.instant();
        for (Map.Entry<PlayerRef, PlayerPresence> entry : store.snapshotAll().entrySet()) {
            if (entry.getValue().isIdlePast(now, idleThreshold)) {
                PlayerRef who = entry.getKey();
                scheduler.onEntity(who, () -> markAfk.markAuto(who));
            }
        }
    }
}
