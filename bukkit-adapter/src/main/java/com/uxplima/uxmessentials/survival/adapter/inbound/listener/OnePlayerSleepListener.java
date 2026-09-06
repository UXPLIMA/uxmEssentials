package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.survival.domain.SleepThreshold;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One-player-sleep: skip the night once enough of a world's eligible players are asleep. On {@link PlayerBedEnterEvent}
 * (and again on {@link PlayerBedLeaveEvent}) it counts the world's sleeping and eligible players and, if the pure
 * {@link SleepThreshold} is met, advances that world to morning and clears the storm, the same effect a full vanilla
 * sleep has. Eligible means online in the world, not spectating, and not sleep-ignored, so an AFK player the presence
 * context has flagged {@link Player#setSleepingIgnored(boolean)} neither counts toward nor blocks the skip.
 *
 * <p>The whether-to-skip decision is the pure {@link SleepThreshold}; this listener only supplies the live counts. The
 * player entering the bed is counted as a sleeper even though {@link PlayerBedEnterEvent} fires a moment before the
 * server records them asleep, so a lone player's first sleep skips the night immediately under the default count of one.
 *
 * <h2>Folia</h2>
 * The bed event fires on the bed's region thread, but the roster enumeration and the world-time change are global game
 * state, so the work hops to the global region through the {@link Scheduler} port. The one place that serialisation is
 * correct, mirroring how {@code /time} advances a world.
 */
@NullMarked
public final class OnePlayerSleepListener implements Listener {

    /** Where a skipped night lands the world: morning, with the sun already up. */
    private static final long MORNING_TICKS = 1000L;

    private final SleepThreshold threshold;
    private final Scheduler scheduler;

    public OnePlayerSleepListener(SleepThreshold threshold, Scheduler scheduler) {
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!event.enterAction().canSleep().success()) {
            return; // the player cannot actually sleep here (wrong time, obstructed), so nothing to evaluate
        }
        Player player = event.getPlayer();
        World world = player.getWorld();
        UUID entering = player.getUniqueId();
        scheduler.onGlobal(() -> advanceIfEnoughSleeping(world, entering));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        World world = event.getPlayer().getWorld();
        scheduler.onGlobal(() -> advanceIfEnoughSleeping(world, null));
    }

    /**
     * Count {@code world}'s sleeping and eligible players and, if the threshold is met, advance it to morning. Runs on
     * the global region thread. {@code enteringSleeper}, when non-null, is counted as a sleeper even before the server
     * records them asleep (the bed-enter case).
     */
    void advanceIfEnoughSleeping(World world, @Nullable UUID enteringSleeper) {
        int eligible = 0;
        int sleepers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)
                    || player.getGameMode() == GameMode.SPECTATOR
                    || player.isSleepingIgnored()) {
                continue;
            }
            eligible++;
            if (player.isSleeping() || player.getUniqueId().equals(enteringSleeper)) {
                sleepers++;
            }
        }
        if (threshold.isMet(sleepers, eligible)) {
            world.setTime(MORNING_TICKS);
            world.setStorm(false);
            world.setThundering(false);
        }
    }
}
