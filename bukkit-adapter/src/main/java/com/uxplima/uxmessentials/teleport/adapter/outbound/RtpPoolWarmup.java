package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.RtpPoolPrewarm;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import org.jspecify.annotations.NullMarked;

/**
 * The enable-time startup sequence for the persisted RTP pool: sweep the aged-out columns, then pre-warm each loaded
 * world's in-memory queue from the columns still on disk. It is the payoff of persisting the pool, the first {@code
 * /rtp} per world after a restart serves from a warm queue instead of triggering a cold search storm.
 *
 * <p>The world iteration reads the live server on the enable (global) thread, but every heavy step is dispatched off
 * it: {@link RtpPoolStore#deleteStale} runs through the {@link Scheduler}'s async dispatch, and each per-world
 * pre-warm loads and re-probes columns off-tick and tick-sliced (the P1 discipline), so warming never storms. A world
 * with no players online is skipped when {@code prewarm-requires-players} is set, so an empty server does no warming
 * work it will not use.
 */
@NullMarked
public final class RtpPoolWarmup {

    private final RtpPoolStore store;
    private final RtpPoolPrewarm prewarm;
    private final PrewarmedSafeLocationQueue queue;
    private final Server server;
    private final Scheduler scheduler;
    private final RtpPoolSettings settings;
    private final int prewarmLimit;
    private final Logger log;
    private final BooleanSupplier running;

    public RtpPoolWarmup(
            RtpPoolStore store,
            RtpPoolPrewarm prewarm,
            PrewarmedSafeLocationQueue queue,
            Server server,
            Scheduler scheduler,
            RtpPoolSettings settings,
            int prewarmLimit,
            Logger log,
            BooleanSupplier running) {
        this.store = Objects.requireNonNull(store, "store");
        this.prewarm = Objects.requireNonNull(prewarm, "prewarm");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.prewarmLimit = prewarmLimit;
        this.log = Objects.requireNonNull(log, "log");
        this.running = Objects.requireNonNull(running, "running");
    }

    /** Sweep stale columns off-tick, then pre-warm every eligible world's queue from the persisted pool. */
    public void start() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.async(this::sweepStale);
        for (World world : server.getWorlds()) {
            if (settings.prewarmRequiresPlayers() && world.getPlayers().isEmpty()) {
                continue;
            }
            queue.prewarm(prewarm, new WorldRef(world.getUID(), world.getName()), prewarmLimit);
        }
    }

    private void sweepStale() {
        try {
            int removed = store.deleteStale(settings.staleAfter());
            if (removed > 0) {
                log.debug("rtp pool swept {} stale columns on enable", removed);
            }
        } catch (RuntimeException failure) {
            log.warn("rtp pool stale sweep failed: {}", String.valueOf(failure.getMessage()));
        }
    }
}
