package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import org.jspecify.annotations.NullMarked;

/**
 * Farm-assist: right-clicking a mature configured crop harvests its drops and replants the same crop in one motion,
 * spending one matching seed from the player's inventory. When the player has no matching seed the crop is still
 * harvested but not replanted, so the mechanic never grants a free replant. Gated by the
 * {@code uxmessentials.survival.farmassist} permission.
 *
 * <p>Only a crop the operator configured (resolved to a {@link Material} → seed {@link Material} map at wiring time)
 * and grown to its maximum {@link Ageable} age is acted on; a young crop is left to keep growing, and a right-click on
 * anything else is ignored. The interaction is cancelled so the held item is not also used (a hoe swing, a block
 * placed), which is what stops the plain hand-break exploit. The player harvests through the assist, not by breaking
 * the plant.
 *
 * <h2>Folia</h2>
 * The interact event runs on the region owning the crop, so the harvest, the seed spend, and the replant all mutate
 * state the event thread already owns: no scheduler hop is needed.
 */
@NullMarked
public final class FarmAssistListener implements Listener {

    private static final String PERMISSION = "uxmessentials.survival.farmassist";

    private final Map<Material, Material> cropToSeed;

    public FarmAssistListener(Map<Material, Material> cropToSeed) {
        this.cropToSeed = Map.copyOf(Objects.requireNonNull(cropToSeed, "cropToSeed"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHarvest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return; // only the main-hand right-click, so the off-hand pass does not double-harvest
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Material seed = cropToSeed.get(block.getType());
        Player player = event.getPlayer();
        if (seed == null || !player.hasPermission(PERMISSION) || !isMature(block)) {
            return;
        }
        event.setCancelled(true);
        harvestAndReplant(block, seed, player);
    }

    /** Harvest the mature crop's drops and, only if the player can pay a seed, replant it at age zero. */
    private void harvestAndReplant(Block block, Material seed, Player player) {
        Material crop = block.getType();
        boolean replant = consumeSeed(player, seed);
        block.breakNaturally();
        if (replant) {
            block.setType(crop);
            if (block.getBlockData() instanceof Ageable fresh) {
                fresh.setAge(0);
                block.setBlockData(fresh);
            }
        }
    }

    /** Whether {@code block} is an {@link Ageable} crop grown to its maximum age. */
    private static boolean isMature(Block block) {
        return block.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge();
    }

    /** Remove one {@code seed} from the player's inventory, returning whether one was present and spent. */
    private static boolean consumeSeed(Player player, Material seed) {
        PlayerInventory inventory = player.getInventory();
        if (!inventory.contains(seed)) {
            return false;
        }
        inventory.removeItem(new ItemStack(seed, 1));
        return true;
    }
}
