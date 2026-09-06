package com.uxplima.uxmessentials.presence.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.domain.ActivityKind;
import com.uxplima.uxmessentials.presence.domain.ActivityPolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The activity listener feeding the AFK clock (docs/02-concurrency.md §6.9): a player who moves, chats, or
 * reels a fishing rod is active, so their idle clock resets and an AFK player returns on the first sign of
 * life, unless the operator's anti-machine {@link ActivityPolicy} ignores that kind, in which case an AFK-farm
 * machine producing only ignored activity still drifts idle and goes AFK.
 *
 * <p>The Bukkit event → {@link ActivityKind} mapping is this adapter's job; the which-kinds-count decision is
 * the pure policy's. A {@link PlayerMoveEvent} is classified by distance: a position change above the move
 * threshold is a real {@link ActivityKind#MOVE} (never ignorable), one at or below it is a sub-threshold
 * {@link ActivityKind#ROTATE} (the operator may ignore it, so a piston nudge does not keep a farm awake).
 *
 * <ul>
 *   <li><b>Move / fish</b>. Fire on the player's own region thread (sync); when the kind counts, activity is
 *       recorded directly, valid because the use case only touches the {@code ConcurrentHashMap} via
 *       {@code compute}.
 *   <li><b>Chat</b>. {@link AsyncChatEvent} fires off the main thread, so the handler bridges back to the
 *       player's region thread through the {@link Scheduler} port before recording activity.
 * </ul>
 */
@NullMarked
public final class PresenceActivityListener implements Listener {

    private final ClearAfkOnActivity clearAfk;
    private final Scheduler scheduler;
    private final ActivityPolicy policy;
    private final double moveThresholdBlocks;

    public PresenceActivityListener(
            ClearAfkOnActivity clearAfk, Scheduler scheduler, ActivityPolicy policy, double moveThresholdBlocks) {
        this.clearAfk = Objects.requireNonNull(clearAfk, "clearAfk");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.moveThresholdBlocks = moveThresholdBlocks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        classifyMove(event.getFrom(), event.getTo())
                .filter(policy::countsAsActivity)
                .ifPresent(kind -> clearAfk.recordActivity(BukkitRefs.toRef(event.getPlayer())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (policy.countsAsActivity(ActivityKind.FISH)) {
            clearAfk.recordActivity(BukkitRefs.toRef(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!policy.countsAsActivity(ActivityKind.CHAT)) {
            return;
        }
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        scheduler.onEntity(who, () -> clearAfk.recordActivity(who));
    }

    /**
     * Classify a position change: a horizontal displacement above the move threshold is a real {@code MOVE},
     * a smaller displacement (including a pure head turn) is sub-threshold {@code ROTATE}, and no displacement
     * at all is not activity. A zero threshold restores the legacy block-hop rule.
     */
    private Optional<ActivityKind> classifyMove(Location from, Location to) {
        boolean blockHop = from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
        if (moveThresholdBlocks <= 0.0) {
            return Optional.of(blockHop ? ActivityKind.MOVE : ActivityKind.ROTATE);
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq > moveThresholdBlocks * moveThresholdBlocks) {
            return Optional.of(ActivityKind.MOVE);
        }
        return blockHop || distanceSq > 0.0 ? Optional.of(ActivityKind.ROTATE) : Optional.empty();
    }
}
