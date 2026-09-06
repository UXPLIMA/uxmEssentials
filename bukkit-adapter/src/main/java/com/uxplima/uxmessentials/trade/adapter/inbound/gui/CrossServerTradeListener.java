package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import org.jspecify.annotations.NullMarked;

/**
 * The one way a cross-server trade ends that the window itself cannot report: the local player disconnecting.
 * Everything they do inside the window (the confirm, a stack staked, the close) reaches the view through the menu
 * engine, which owns the window.
 *
 * <p>The handler runs at {@link EventPriority#LOWEST} so it claims the escrow before the engine's own quit teardown
 * reads the window back, which is what lets the abort hand the items over on the thread the player is quitting from.
 */
@NullMarked
public final class CrossServerTradeListener implements Listener {

    private final CrossServerTradeView view;

    public CrossServerTradeListener(CrossServerTradeView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        view.onQuit(event.getPlayer());
    }
}
