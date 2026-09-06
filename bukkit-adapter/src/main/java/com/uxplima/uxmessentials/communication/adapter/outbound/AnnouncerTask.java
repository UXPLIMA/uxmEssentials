package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The rotating announcer: self-rescheduling tasks on the {@link Scheduler} port (docs/02-concurrency.md §6.10
 * self-rescheduling-loop pattern, matching the presence AFK sweep and the messaging mail-expiry sweep).
 *
 * <p>There are two cadences. Announcements <em>without</em> an interval override rotate together on the config-wide
 * default interval: each default tick asks {@link NextAnnouncement#pick(int)} (the cursor + ordering + no-repeat
 * rule, gated by min-players) for the next one and broadcasts it. Each announcement that declares its own
 * {@code interval-seconds} runs on its <em>own</em> independent loop at that cadence, re-resolved from the live
 * config by id each tick (so a reload that changes its interval or drops it is honoured) and gated by the same
 * min-players check. An override announcement is therefore not part of the shared rotation.
 *
 * <p>The default interval and the per-announcement overrides are read fresh from the live {@link AnnouncerConfig}
 * each reschedule, so a {@code /announce reload} (or {@code /uxmess reload communication}) that swaps a new config
 * in changes a cadence on the next tick without re-arming the task. A reload that <em>adds</em> a brand-new override
 * announcement, however, needs a loop armed for it. That announcement is excluded from the shared rotation, so
 * without {@link #rearmOverrides()} it would silently never broadcast. {@link #rearmOverrides()} cancels every
 * currently-armed override loop and arms one fresh loop per announcement that currently declares an override.
 *
 * <p>The {@link Scheduler} port is fire-and-forget with no cancellation handle, so each override loop carries its
 * own {@link AtomicBoolean} cancellation flag (the pattern the port's Javadoc endorses): the loop checks both the
 * module's {@code running} flag and its own flag each tick and exits cleanly when either is set. The task tracks the
 * live loops so {@link #rearmOverrides()} and {@link #stop()} can flip them all and let the orphaned ticks die.
 * Delivery flows through the {@link BukkitAnnouncerBroadcaster}, which enumerates the online set on the global thread
 * and hops to each viewer's region thread, gating by opt-out and the announcement's display condition; no Bukkit
 * entity is touched on the async loop itself.
 */
@NullMarked
public final class AnnouncerTask {

    private final Scheduler scheduler;
    private final NextAnnouncement nextAnnouncement;
    private final BukkitAnnouncerBroadcaster broadcaster;
    private final Supplier<AnnouncerConfig> config;
    private final BooleanSupplier running;

    // The live override loops, each holding its own cancellation flag. Owned by the thread that calls start /
    // rearmOverrides / stop (the wiring thread on enable, the command's async reload thread on reload); guarded by
    // its own monitor because a reload can race the in-flight ticks that only read it via their captured loop.
    private final List<OverrideLoop> overrideLoops = new ArrayList<>();

    public AnnouncerTask(
            Scheduler scheduler,
            NextAnnouncement nextAnnouncement,
            BukkitAnnouncerBroadcaster broadcaster,
            Supplier<AnnouncerConfig> config,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.nextAnnouncement = Objects.requireNonNull(nextAnnouncement, "nextAnnouncement");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.config = Objects.requireNonNull(config, "config");
        this.running = Objects.requireNonNull(running, "running");
    }

    /**
     * Arm the default rotation loop and one independent loop per override announcement present at start. The override
     * loops are armed through the same path as {@link #rearmOverrides()}.
     */
    public void start() {
        scheduleDefaultRotation();
        rearmOverrides();
    }

    /**
     * Cancel every currently-armed override loop and arm a fresh loop per announcement that currently declares an
     * {@code interval-seconds} override, reading the live (just-reloaded) config. Idempotent and safe to call
     * repeatedly: a second call with an unchanged config cancels the loops it armed and arms equivalent ones. The
     * default rotation loop is untouched: it already re-resolves {@link AnnouncerConfig#rotating()} each tick.
     */
    public void rearmOverrides() {
        synchronized (overrideLoops) {
            cancelOverrideLoops();
            if (!running.getAsBoolean()) {
                return;
            }
            for (Announcement announcement : config.get().announcements()) {
                announcement.intervalOverride().ifPresent(interval -> armOverrideLoop(announcement.id(), interval));
            }
        }
    }

    /** Stop the announcer: flip every override loop's cancellation flag so no orphaned tick survives the disable. */
    public void stop() {
        synchronized (overrideLoops) {
            cancelOverrideLoops();
        }
    }

    // Caller must hold the overrideLoops monitor.
    private void cancelOverrideLoops() {
        for (OverrideLoop loop : overrideLoops) {
            loop.cancel();
        }
        overrideLoops.clear();
    }

    // Caller must hold the overrideLoops monitor.
    private void armOverrideLoop(String id, Duration interval) {
        OverrideLoop loop = new OverrideLoop(id);
        overrideLoops.add(loop);
        scheduleOverride(loop, interval);
    }

    private void scheduleDefaultRotation() {
        if (!running.getAsBoolean()) {
            return;
        }
        Duration interval = config.get().defaultInterval();
        scheduler.asyncAfter(interval, this::tickDefaultRotation);
    }

    private void tickDefaultRotation() {
        if (!running.getAsBoolean()) {
            return;
        }
        // The roster size feeding the min-players gate is read on the global region thread (Folia forbids reading
        // Bukkit.getOnlinePlayers() off it); the pick and the broadcast run from that same global hop so the gate and
        // the fan-out see one consistent roster.
        broadcaster.withOnlineCount(count -> nextAnnouncement.pick(count).ifPresent(broadcaster::broadcast));
        scheduleDefaultRotation();
    }

    private void scheduleOverride(OverrideLoop loop, Duration interval) {
        if (!running.getAsBoolean() || loop.cancelled()) {
            return;
        }
        scheduler.asyncAfter(interval, () -> tickOverride(loop));
    }

    private void tickOverride(OverrideLoop loop) {
        if (!running.getAsBoolean() || loop.cancelled()) {
            return; // the module is disabled, or this loop was cancelled by a reload; let it die
        }
        AnnouncerConfig live = config.get();
        Optional<Announcement> current = find(live, loop.id());
        if (current.isEmpty() || current.get().intervalOverride().isEmpty()) {
            return; // the announcement was dropped or lost its override on reload; let this loop die
        }
        Announcement announcement = current.get();
        // The min-players gate reads the roster size on the global region thread; the broadcast fans out from that
        // same global hop so the gate and the fan-out agree on the live roster.
        broadcaster.withOnlineCount(count -> {
            if (live.shouldFire(count)) {
                broadcaster.broadcast(announcement);
            }
        });
        scheduleOverride(loop, announcement.intervalOverride().orElseThrow());
    }

    private static Optional<Announcement> find(AnnouncerConfig config, String id) {
        return config.announcements().stream()
                .filter(announcement -> announcement.id().equals(id))
                .findFirst();
    }

    /**
     * One override announcement's independent loop. Its {@code cancelled} flag is the loop's cancellation handle:
     * {@link #rearmOverrides()} and {@link #stop()} set it, and the next tick (or pending reschedule) observes it
     * and exits, since the fire-and-forget {@link Scheduler} hands back no handle to cancel directly.
     */
    private static final class OverrideLoop {

        private final String id;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        OverrideLoop(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        boolean cancelled() {
            return cancelled.get();
        }

        void cancel() {
            cancelled.set(true);
        }
    }
}
