package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;

/**
 * Opens a custom menu when the player right-clicks the opener item that targets it. The held item is recognised by
 * its persistent tag (read by {@link OpenerItems#menuOf}), not its material or name, so the operator may skin the
 * opener however they like. On a match the menu is opened through the shared {@link Menus} façade and the interaction
 * is cancelled so the item is not also used/placed.
 *
 * <p>Cheap gates run first so an ordinary interact stays hot-path-light: only a right-click with the main hand is
 * considered (the {@link EquipmentSlot#HAND} guard makes the event fire once rather than twice for the two hands),
 * the item must be a real, tagged opener, and the tagged menu must still be registered. A tag whose menu was since
 * deleted or renamed is a silent no-op, never an error. The event fires on the player's own region thread, and the
 * façade opens the window on that same thread, so this stays Folia-safe.
 */
public final class MenuOpenerInteractListener implements Listener {

    private final Menus menus;
    private final Supplier<List<String>> menuNames;
    private final OpenerItems openerItems;

    public MenuOpenerInteractListener(Menus menus, Supplier<List<String>> menuNames, OpenerItems openerItems) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.menuNames = Objects.requireNonNull(menuNames, "menuNames");
        this.openerItems = Objects.requireNonNull(openerItems, "openerItems");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isRightClick(event.getAction()) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        Optional<String> menuId = openerItems.menuOf(item);
        if (menuId.isEmpty() || !menuNames.get().contains(menuId.get())) {
            return;
        }
        event.setCancelled(true);
        menus.open(BukkitRefs.toRef(event.getPlayer()), menuId.get(), null);
    }

    private static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }
}
