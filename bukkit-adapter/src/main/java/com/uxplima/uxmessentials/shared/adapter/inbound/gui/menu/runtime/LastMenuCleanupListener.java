package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import org.jspecify.annotations.NullMarked;

/**
 * Drops a quitting player's remembered {@code /menu last} target so the {@link LastMenu} map tracks only the online
 * set. The engine already forgets one open when it records the next, so the map is naturally bounded while a player
 * is online; this closes the single gap, a player who logs off and never returns, that would otherwise leave a
 * stale entry until the same UUID opens another menu. The clear only touches an in-memory map, so it runs inline on
 * the quit event, mirroring the player-data cache eviction.
 */
@NullMarked
public final class LastMenuCleanupListener implements Listener {

    private final LastMenu lastMenu;

    public LastMenuCleanupListener(LastMenu lastMenu) {
        this.lastMenu = Objects.requireNonNull(lastMenu, "lastMenu");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastMenu.clear(event.getPlayer().getUniqueId());
    }
}
