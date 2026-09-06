package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import org.jspecify.annotations.NullMarked;

/**
 * The two ways a trade ends that the window itself cannot report: a participant disconnecting and a participant
 * changing world. Everything a player does inside the window. A click on a control, a stack placed in their offer,
 * a close, reaches the view through the menu engine, which owns the window; these two events do not, so they are
 * routed here.
 *
 * <p>The quit handler runs at {@link EventPriority#LOWEST} so it claims the settlement before the engine's own quit
 * teardown reads the window back. Both paths lead to the same place, but this one can still hand the quitter their
 * items on the thread they are quitting from, which is the last moment their inventory can be written to.
 */
@NullMarked
public final class TradeListener implements Listener {

    private final TradeView view;

    public TradeListener(TradeView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        view.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        view.onLeave(event.getPlayer().getUniqueId());
    }
}
