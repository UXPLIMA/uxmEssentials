package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.custommenus.adapter.MenuEditLocks;
import org.jspecify.annotations.NullMarked;

/**
 * Releases a disconnecting operator's menu edit lock, so a menu is never left pinned by someone who has left the
 * server. The in-game editor takes the {@link MenuEditLocks} lock when a viewer opens a menu's grid or property
 * editor and releases it when they return to the menu browser; this listener closes the last gap, a quit while a
 * menu is still open, so the lock always frees eventually. A quitting player who held no lock is a harmless no-op.
 */
@NullMarked
public final class MenuEditLockListener implements Listener {

    private final MenuEditLocks locks;

    public MenuEditLockListener(MenuEditLocks locks) {
        this.locks = Objects.requireNonNull(locks, "locks");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        locks.release(event.getPlayer().getUniqueId());
    }
}
