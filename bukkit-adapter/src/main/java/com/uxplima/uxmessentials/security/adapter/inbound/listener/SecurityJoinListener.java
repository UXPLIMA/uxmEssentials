package com.uxplima.uxmessentials.security.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.security.adapter.VerificationController;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * The connection edges of the join-verification freeze: on join it hands the player to the {@link
 * VerificationController}, which decides off the tick thread whether they must prove a second factor and freezes them
 * if so; on quit it drops any pending freeze so a disconnect mid-verification leaves no lingering state. Both fire at
 * {@code MONITOR}. The decision only reads state and schedules its own work, so it runs after every other join
 * handler has settled the player in.
 */
@NullMarked
public final class SecurityJoinListener implements Listener {

    private final VerificationController controller;

    public SecurityJoinListener(VerificationController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        controller.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        controller.onQuit(BukkitRefs.toRef(event.getPlayer()));
    }
}
