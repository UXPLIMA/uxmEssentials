package com.uxplima.uxmessentials.staff.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffGadgetItems;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffFollowService;
import org.jspecify.annotations.NullMarked;

/**
 * The staff-mode interaction listener: it turns a right-click on a gadget item into the gadget's action, keeps
 * the gadget items from leaking out of the gadget hotbar, and guarantees a quit-in-mode never loses items.
 *
 * <ul>
 *   <li><b>Gadget use.</b> An interact with a gadget item resolves the gadget by its PDC tag (never by material
 *       or name) and routes it through {@link StaffGadgetActions}: an interact that hits no player goes to the
 *       air path (VANISH/EXAMINE/COMPASS act, FREEZE/FOLLOW say "look at a player"), while a right-click landing
 *       directly on a player entity goes to the player path (FREEZE/FOLLOW/COMPASS act on that target). Both
 *       interactions are cancelled so the gadget item never also places/uses or harms the clicked player.
 *   <li><b>No leak.</b> Dropping a gadget item, moving it in any inventory view, or F-key swapping it into the
 *       off-hand is cancelled, so a gadget can never reach the ground, the off-hand, or survive into the restored
 *       real inventory.
 *   <li><b>Quit safety.</b> On quit while in staff mode the real loadout is restored straight away (the quit event
 *       still has a usable player on its own region thread), so a player never logs out stranded in the gadget
 *       hotbar; the committed loadout row is the crash fallback. The quit also ends any follow the player was
 *       driving.
 * </ul>
 */
@NullMarked
public final class StaffModeListener implements Listener {

    private final StaffServices services;
    private final StaffGadgetItems gadgetItems;
    private final StaffFollowService followService;
    private final StaffGadgetActions actions;

    public StaffModeListener(
            StaffServices services,
            StaffGadgetItems gadgetItems,
            StaffFollowService followService,
            StaffGadgetActions actions) {
        this.services = Objects.requireNonNull(services, "services");
        this.gadgetItems = Objects.requireNonNull(gadgetItems, "gadgetItems");
        this.followService = Objects.requireNonNull(followService, "followService");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isUse(event.getAction()) || event.getHand() == null) {
            return;
        }
        ItemStack hand = event.getItem();
        var gadget = gadgetItems.gadgetOf(hand);
        if (gadget.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        actions.onAir(gadget.get(), player, BukkitRefs.toRef(player));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        var gadget = gadgetItems.gadgetOf(hand);
        if (gadget.isEmpty() || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        event.setCancelled(true);
        Player actor = event.getPlayer();
        actions.onPlayer(gadget.get(), actor, BukkitRefs.toRef(actor), target, BukkitRefs.toRef(target));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (gadgetItems.isGadget(event.getItemDrop().getItemStack())) {
            // Never let a gadget item reach the ground; it must stay in the hotbar to be cleared on exit.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (gadgetItems.isGadget(event.getCurrentItem()) || gadgetItems.isGadget(event.getCursor())) {
            // Cancel any move of a gadget item so it cannot be shuffled into a slot the restore would keep.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (gadgetItems.isGadget(event.getMainHandItem()) || gadgetItems.isGadget(event.getOffHandItem())) {
            // The F-key off-hand swap would strand a gadget in the off-hand (outside the hotbar the exit clears);
            // cancel it so a gadget never escapes into a slot the restore would keep.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // End any follow this player was driving; the follow task would otherwise notice next tick, but stopping
        // here keeps the session map tight on logout.
        followService.stop(player.getUniqueId());
        PlayerRef who = BukkitRefs.toRef(player);
        if (services.store().isActive(who)) {
            // Restore the real loadout now; the player is still usable on this region thread. The DB row is the
            // crash fallback if the process dies before this runs.
            services.exit().exit(who);
        }
    }

    private static boolean isUse(Action action) {
        return action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK;
    }
}
