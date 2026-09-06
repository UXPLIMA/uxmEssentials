package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuItemMark;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Strips escaped menu display items from a player's real inventory, the persistence/escape half of the anti-dupe
 * defence. The engine already cancels every click on a menu window, so a display item should never leave the top
 * inventory in the first place; this listener is the safety net for the paths a cancel-all cannot cover, a plugin
 * conflict, a crash with a menu open, or the item being written to the player file at shutdown, where a
 * {@link MenuItemMark marked} tile could end up in a genuine inventory and be duplicated. It is a separate listener
 * from {@link MenuListener} on purpose: the anti-dupe is a distinct safety concern, not part of the one-listener menu
 * click-routing invariant (the same separation {@link LastMenuCleanupListener} keeps for its own cleanup).
 *
 * <p>Only items carrying the menu mark are ever removed, and only from the player's <em>own</em> inventory, never the
 * closing menu window (which is discarded anyway). A real inventory of genuine items is left byte-identical because
 * genuine items are never marked; a menu tile is marked on a display copy, so the mark reliably distinguishes an
 * escaped copy from something the player owns. Both events fire on the player's own region/event thread, and the sweep
 * reads and writes only that player's inventory, so it is Folia-safe inline with no scheduler hop.
 */
@NullMarked
public final class MenuAntiDupeListener implements Listener {

    private final Logger log;

    public MenuAntiDupeListener(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /** On close, sweep the closing player's own inventory: a marked tile there escaped the cancelled menu window. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            strip(player);
        }
    }

    /** On join, sweep the inventory a marked tile may have persisted into across a restart (menu open at shutdown). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        strip(event.getPlayer());
    }

    /**
     * Remove every marked menu tile from {@code player}'s own inventory, leaving genuine items untouched. The contents
     * span the storage, armour and off-hand slots, and {@code setItem} clears each by the same index the read returned.
     * A marked item is abnormal, the cancel-all invariant should never let one land here, so each removal is logged as
     * an operator diagnostic; the common empty sweep logs nothing.
     */
    private void strip(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (MenuItemMark.isMarked(contents[slot])) {
                inventory.setItem(slot, null);
                log.debug("event=menu_antidupe_stripped player={} slot={}", player.getName(), slot);
            }
        }
    }
}
