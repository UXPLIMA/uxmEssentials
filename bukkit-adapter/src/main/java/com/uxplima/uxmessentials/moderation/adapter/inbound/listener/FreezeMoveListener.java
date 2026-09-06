package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import com.uxplima.uxmessentials.moderation.adapter.outbound.BukkitSanctions;
import org.jspecify.annotations.NullMarked;

/**
 * Pins a frozen player in place: a {@link PlayerMoveEvent} that changes the block position of a frozen player
 * is cancelled, so they can look around but not walk. Reads the session-scoped frozen set the
 * {@link BukkitSanctions} adapter owns; a relog clears the freeze (the set is in-memory), so this listener
 * naturally stops blocking once the set no longer contains the player.
 *
 * <p>The block-position check means head movement (yaw/pitch only) is not cancelled, cancelling every micro
 * move would be both jarring and needless event churn on the hot move path.
 */
@NullMarked
public final class FreezeMoveListener implements Listener {

    private final BukkitSanctions sanctions;

    public FreezeMoveListener(BukkitSanctions sanctions) {
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!sanctions.isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        if (movedBlock(event)) {
            event.setCancelled(true);
        }
    }

    private static boolean movedBlock(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
