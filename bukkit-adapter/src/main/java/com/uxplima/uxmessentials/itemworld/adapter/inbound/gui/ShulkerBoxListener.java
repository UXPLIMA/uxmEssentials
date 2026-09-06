package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens a shulker box the player right-clicks in hand as an in-inventory {@link ShulkerBoxView}, and keeps the open
 * view dupe-safe. Opening is gated cheapest-first so an ordinary right-click stays hot-path-light: the shulker
 * sub-feature must be enabled ({@link ItemworldConfig#shulkersEnabled()}), the interaction must be a main-hand
 * right-click with a shulker box in hand, the sneak trigger (if required) must be met, and the player must hold
 * {@code uxmessentials.itemworld.shulker}. When it opens, the interaction is cancelled so the box is not also placed
 * as a block. A player who lacks the permission or the sub-feature simply keeps vanilla behaviour, a silent no-op.
 *
 * <p>While a view is open the click/drag policy ({@link ShulkerMenuPolicy}) locks the source box in its slot and
 * refuses to nest a shulker inside the view; on close the {@link ShulkerBoxView} writes the edits back into the box.
 * The interact and close events fire on the player's own region thread, so both open and write-back are valid there.
 * The listener shares the {@code inbound.gui} package with the view and holder, mirroring the sanctioned invsee /
 * offline-container leaves.
 */
@NullMarked
public final class ShulkerBoxListener implements Listener {

    private static final String PERMISSION = "uxmessentials.itemworld.shulker";

    private final ShulkerBoxView view;
    private final ItemworldConfig config;

    public ShulkerBoxListener(ShulkerBoxView view, ItemworldConfig config) {
        this.view = Objects.requireNonNull(view, "view");
        this.config = Objects.requireNonNull(config, "config");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.shulkersEnabled() || event.getHand() != EquipmentSlot.HAND || !isRightClick(event.getAction())) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!ShulkerMenuPolicy.isShulker(hand)) {
            return;
        }
        Player player = event.getPlayer();
        if (config.shulkersRequireSneak() && !player.isSneaking()) {
            return;
        }
        if (!player.hasPermission(PERMISSION)) {
            return;
        }
        event.setCancelled(true);
        view.open(player, BukkitRefs.toRef(player));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        ShulkerBoxHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null && ShulkerMenuPolicy.clickBlocked(event, holder.sourceSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        ShulkerBoxHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null && ShulkerMenuPolicy.dragBlocked(event, holder.sourceSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        ShulkerBoxHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null && event.getPlayer() instanceof Player closer) {
            view.onClose(holder, closer);
        }
    }

    /**
     * Write back a view its owner still had open as they disconnect. The close handler is not a reliable last word on
     * a quit, and a view left unwritten duplicates whatever was dragged out of the box, so the quit claims it too.
     * Harmless when the close already ran: the view tracks each player once and only one of the two claims it.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        view.onQuit(event.getPlayer());
    }

    private static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private static @Nullable ShulkerBoxHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof ShulkerBoxHolder shulker ? shulker : null;
    }
}
