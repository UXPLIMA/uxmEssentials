package com.uxplima.uxmessentials.villagers.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;

/**
 * Lets a permitted player leash a villager, a lead vanilla never lets you attach to a villager. Vanilla offers no
 * leash interaction for a villager (so {@code PlayerLeashEntityEvent} never fires for one); instead the right-click
 * opens the trade window. This listener intercepts that right-click when the player holds a lead: it attaches the
 * lead through the API, consumes one lead from their hand, and cancels the event so the trade window does not also
 * open: the vanilla "can't leash a villager" outcome is replaced with a real leash.
 *
 * <p>The player must hold {@code uxmessentials.villagers.leash} and a {@link Material#LEAD} in the main hand, and a
 * villager already on a lead is left alone. With the feature off, {@code enabled} is {@code false} and the handler is
 * an inert early return.
 *
 * <h2>Folia</h2>
 * The interaction is dispatched on the region owning the clicked villager (the player is right there interacting with
 * it), so the leash is attached on that same thread with no scheduler hop.
 */
@NullMarked
public final class VillagerLeashListener implements Listener {

    private final boolean enabled;
    private final String leashPermission;

    public VillagerLeashListener(boolean enabled, String leashPermission) {
        this.enabled = enabled;
        this.leashPermission = Objects.requireNonNull(leashPermission, "leashPermission");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!enabled
                || event.getHand() != EquipmentSlot.HAND
                || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        Player player = event.getPlayer();
        if (villager.isLeashed()
                || player.getInventory().getItemInMainHand().getType() != Material.LEAD
                || !player.hasPermission(leashPermission)) {
            return;
        }
        if (!villager.setLeashHolder(player)) {
            return; // the lead could not attach, leave the interaction for vanilla to resolve
        }
        event.setCancelled(true);
        consumeOneLead(player);
    }

    // A leash consumes the lead into the physical tether, exactly as vanilla leashing does; creative keeps its lead.
    private static void consumeOneLead(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack inHand = player.getInventory().getItemInMainHand();
        inHand.setAmount(inHand.getAmount() - 1);
    }
}
