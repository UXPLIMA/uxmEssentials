package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The nametags reconcile-and-animate timer: a self-rescheduling task on the {@link Scheduler} port
 * (docs/02-concurrency.md §6.10 self-rescheduling-loop pattern, matching the tablist and scoreboard render timers).
 * With the packet renderer owning each wearer's per-viewer text/viewer/line-of-sight refresh (one {@code entityTimer}
 * per wearer inside uxmLib), this task no longer renders. It does the two things the lib loop cannot:
 *
 * <ul>
 *   <li><strong>Format selection.</strong> A permission, world, gamemode, sneak, or show-when change re-selects the
 *       wearer's format. Each tick this hops to every online player's entity thread and calls
 *       {@link PacketNametagPresenter#update}, which re-selects and (when the selected format or its appearance changed)
 *       removes-then-re-shows, or removes the nametag when no format applies any more.</li>
 *   <li><strong>The global animation clock.</strong> The animation clock is global, not per-wearer: this loop calls
 *       {@link AnimationRegistry#advance()} exactly once per tick on the loop thread. Stepping every named animation to
 *       the tick's frame, so an animation advances at most once per tick no matter how many wearers are online, and the
 *       lib's per-wearer text callbacks then read the same current frame.</li>
 * </ul>
 *
 * <p>The interval is read fresh each reschedule, so a reload that swaps a new cadence in changes the reconcile rate on
 * the next tick. The task observes the module's {@code running} flag and exits cleanly on disable.
 */
@NullMarked
public final class NametagRenderTask {

    private final Scheduler scheduler;
    private final PacketNametagPresenter presenter;
    private final AnimationRegistry animations;
    private final Logger log;
    private final Supplier<Duration> interval;
    private final BooleanSupplier running;

    public NametagRenderTask(
            Scheduler scheduler,
            PacketNametagPresenter presenter,
            AnimationRegistry animations,
            Logger log,
            Supplier<Duration> interval,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.log = Objects.requireNonNull(log, "log");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.running = Objects.requireNonNull(running, "running");
    }

    /** Arm the first reconcile tick; subsequent ticks reschedule themselves until the module stops. */
    public void start() {
        scheduleNext();
    }

    /** Force every online wearer onto the newly loaded config generation immediately. */
    public void refreshNow() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.onGlobal(() -> {
            if (!running.getAsBoolean()) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                com.uxplima.uxmessentials.shared.domain.PlayerRef ref = BukkitRefs.toRef(player);
                scheduler.onEntity(ref, () -> {
                    Player live = Bukkit.getPlayer(ref.uuid());
                    if (live != null && live.isOnline() && running.getAsBoolean()) {
                        presenter.refresh(live);
                    }
                });
            }
        });
    }

    private void scheduleNext() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.asyncAfter(Objects.requireNonNull(interval.get(), "interval"), this::tick);
    }

    private void tick() {
        if (!running.getAsBoolean()) {
            return;
        }
        try {
            // Advance the global animation clock once, on this loop thread, before fanning out so every per-wearer text
            // callback the lib invokes this period reads the same frame.
            animations.advance();
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.onEntity(BukkitRefs.toRef(player), () -> presenter.update(player));
            }
        } catch (RuntimeException failure) {
            // A throwing tick must not skip the reschedule below, or nametags would stop reconciling until a reload.
            log.error("nametag reconcile tick failed", failure);
        }
        scheduleNext();
    }
}
