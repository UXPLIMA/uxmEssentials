package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The dupe-safe click/drag policy for the in-inventory shulker view. Unlike the invsee mirrors, the source is an
 * item in the <em>same</em> inventory that forms the bottom of the view, so the policy locks two things: the source
 * box may not leave its hotbar slot ({@link ShulkerBoxHolder#sourceSlot()}), and no shulker box may be placed
 * <em>into</em> the view (nesting a box inside a box). Everything else. Moving ordinary items in and out of the
 * 27-slot view: is the player's to do. Slots are matched by raw index so the policy never depends on the click's
 * slot-conversion, and shulker boxes are unstackable so a single-item lock is complete.
 */
@NullMarked
final class ShulkerMenuPolicy {

    private ShulkerMenuPolicy() {}

    /** Whether a click must be cancelled: it would move the locked source box or nest a shulker into the view. */
    static boolean clickBlocked(InventoryClickEvent event, int sourceSlot) {
        return touchesSource(event, sourceSlot) || nestsShulker(event);
    }

    /** Whether a drag must be cancelled: it touches the locked source slot or drags a shulker into the view. */
    static boolean dragBlocked(InventoryDragEvent event, int sourceSlot) {
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().contains(rawSourceSlot(topSize, sourceSlot))) {
            return true;
        }
        boolean intoView = event.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        return intoView && isShulker(event.getOldCursor());
    }

    private static boolean touchesSource(InventoryClickEvent event, int sourceSlot) {
        int topSize = event.getView().getTopInventory().getSize();
        // The source box's own raw slot (a pickup/drop/swap on it), or a number-key swap targeting its hotbar slot.
        return event.getRawSlot() == rawSourceSlot(topSize, sourceSlot) || event.getHotbarButton() == sourceSlot;
    }

    private static boolean nestsShulker(InventoryClickEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // A shift-click from the bottom (raw >= topSize) pushes the item up into the view.
            return event.getRawSlot() >= topSize && isShulker(event.getCurrentItem());
        }
        if (event.getRawSlot() < topSize) {
            // A placement onto a view slot: the incoming item is the cursor or a hotbar-swapped stack.
            return isShulker(event.getCursor()) || isShulker(hotbarItem(event));
        }
        return false;
    }

    /** The raw slot the opener's hotbar {@code sourceSlot} maps to below a {@code topSize}-slot view (27 storage + hotbar). */
    private static int rawSourceSlot(int topSize, int sourceSlot) {
        return topSize + 27 + sourceSlot;
    }

    private static @Nullable ItemStack hotbarItem(InventoryClickEvent event) {
        int button = event.getHotbarButton();
        if (button < 0 || !(event.getWhoClicked() instanceof Player player)) {
            return null;
        }
        return player.getInventory().getItem(button);
    }

    static boolean isShulker(@Nullable ItemStack item) {
        // Shulker box item types are block materials, so the block-registry tag identifies the item just as well.
        return item != null && Tag.SHULKER_BOXES.isTagged(item.getType());
    }
}
