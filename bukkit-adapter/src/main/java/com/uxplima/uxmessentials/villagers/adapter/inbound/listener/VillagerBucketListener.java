package com.uxplima.uxmessentials.villagers.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerBucketItem;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The villager-in-a-bucket handler. A player holding {@code uxmessentials.villagers.bucket} sneak-right-clicks a
 * villager to pick it up: the villager's profession, type, level, and trades are encoded into a named captured-villager
 * item (through {@link VillagerBucketItem}), the item is handed to the player, and the entity is removed. Right-clicking
 * a block with that item places the villager back, spawning a fresh villager and decoding the stored data onto it before
 * it joins the world, then consuming one item from the placer's hand.
 *
 * <p>Both directions cancel their event so the vanilla interaction. The trade window on pickup, the spawn-egg's own
 * spawn on placement: never also fires. The captured item's stored data is applied inside the spawn callback, so the
 * villager offers its restored trades from the moment it appears rather than a re-rolled vanilla set. With the feature
 * off, {@code enabled} is {@code false} and both handlers are an inert early return.
 *
 * <h2>Folia</h2>
 * The pickup runs on the region owning the clicked villager (the player is right there interacting with it), and the
 * placement runs on the region owning the clicked block, spawning the villager into that same region, each a
 * region-local touch with no scheduler hop.
 */
@NullMarked
public final class VillagerBucketListener implements Listener {

    private final boolean enabled;
    private final String bucketPermission;
    private final VillagerBucketItem bucketItem;
    private final GuiText guiText;

    public VillagerBucketListener(
            boolean enabled, String bucketPermission, VillagerBucketItem bucketItem, GuiText guiText) {
        this.enabled = enabled;
        this.bucketPermission = Objects.requireNonNull(bucketPermission, "bucketPermission");
        this.bucketItem = Objects.requireNonNull(bucketItem, "bucketItem");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(PlayerInteractEntityEvent event) {
        if (!enabled
                || event.getHand() != EquipmentSlot.HAND
                || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission(bucketPermission)) {
            return;
        }
        event.setCancelled(true);
        Component name = guiText.text(BukkitRefs.toRef(player), VillagersMessageKey.VILLAGERS_BUCKET_NAME);
        ItemStack bucket = bucketItem.capture(villager, name);
        villager.remove();
        giveOrDrop(player, villager.getLocation(), bucket);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (!enabled || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        Block clicked = event.getClickedBlock();
        if (item == null || clicked == null || !bucketItem.isBucket(item)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        placeVillager(clicked.getRelative(event.getBlockFace()).getLocation(), item.clone());
        consumeOne(event.getPlayer());
    }

    private void placeVillager(Location corner, ItemStack captured) {
        World world = corner.getWorld();
        if (world == null) {
            return;
        }
        Location spawnAt = corner.add(0.5, 0.0, 0.5);
        world.spawn(spawnAt, Villager.class, villager -> bucketItem.restore(villager, captured));
    }

    private static void consumeOne(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack inHand = player.getInventory().getItemInMainHand();
        inHand.setAmount(inHand.getAmount() - 1);
    }

    private static void giveOrDrop(Player player, Location where, ItemStack bucket) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(bucket);
        World world = where.getWorld();
        if (world != null) {
            overflow.values().forEach(leftover -> world.dropItemNaturally(where, leftover));
        }
    }
}
