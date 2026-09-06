package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Routes the click and drag events for {@code /kit show} preview menus, recognised by their
 * {@link KitPreviewHolder}. The policy is the simplest one: a preview is read-only, so every interaction with the
 * menu is cancelled (a plain click, a shift-click that would shove an item in, a drag that touches the menu) so
 * the player can look but never take. A click in the player's own inventory while a preview is open is left
 * alone; it cannot move anything into the cancelled top. The preview holds no state, so its close needs no
 * handler.
 */
@NullMarked
public final class KitPreviewListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (holderOf(event.getView().getTopInventory()) == null) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        Inventory top = event.getView().getTopInventory();
        // Cancel any interaction that touches the preview itself, or any unbounded move that could push an item
        // into it (a shift-click from the player's own inventory names no destination slot).
        if (clicked == null || clicked.equals(top) || movesIntoTop(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (holderOf(top) == null) {
            return;
        }
        int topSize = top.getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (touchesTop) {
            event.setCancelled(true);
        }
    }

    private static boolean movesIntoTop(InventoryClickEvent event) {
        return switch (event.getAction()) {
            case MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR -> true;
            default -> false;
        };
    }

    private static @Nullable KitPreviewHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof KitPreviewHolder preview ? preview : null;
    }
}
