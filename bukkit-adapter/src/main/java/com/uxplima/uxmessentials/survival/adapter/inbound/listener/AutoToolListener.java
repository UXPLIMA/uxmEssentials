package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.domain.AutoToolSelector;
import com.uxplima.uxmessentials.survival.domain.AutoToolSelector.HeldTool;
import org.jspecify.annotations.NullMarked;

/**
 * Auto-tool: as the player starts breaking a block, their held hotbar slot is switched to the strongest tool of the
 * family that block needs (a pickaxe for stone, an axe for a log). It listens on {@code BlockDamageEvent}, the first
 * tick of mining, before the break resolves, so the correct tool is in hand when the block finally breaks and its
 * drops (and durability) are computed against the right tool. Gated by the {@code uxmessentials.survival.autotool}
 * permission and the per-player toggle.
 *
 * <h2>Selection</h2>
 * The choice is the pure {@link AutoToolSelector}: the block's material name and the player's hotbar are handed to the
 * domain, which names the winning slot or none. This listener only reads the hotbar and applies the swap, so the "which
 * tool is best" rule stays unit-testable off Bukkit.
 *
 * <h2>Folia</h2>
 * The damage event runs on the region owning the block, and the only mutation is the player's own held-slot index, so
 * the swap is applied inline on the event thread with no scheduler hop.
 */
@NullMarked
public final class AutoToolListener implements Listener {

    private static final String PERMISSION = "uxmessentials.survival.autotool";
    private static final int HOTBAR_SIZE = 9;

    private final AutoToolSelector selector;
    private final PdcSurvivalToggles toggles;

    public AutoToolListener(AutoToolSelector selector, PdcSurvivalToggles toggles) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (event.getInstaBreak()) {
            return; // an instant break has already resolved its drops; there is nothing to switch for
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION) || !toggles.autoToolActive(player, true)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        OptionalInt best = selector.bestSlot(event.getBlock().getType().name(), readHotbar(inventory));
        best.ifPresent(slot -> {
            if (slot != inventory.getHeldItemSlot()) {
                inventory.setHeldItemSlot(slot);
            }
        });
    }

    /** Snapshot the occupied hotbar slots as domain tools; empty slots are simply omitted. */
    private static List<HeldTool> readHotbar(PlayerInventory inventory) {
        List<HeldTool> hotbar = new ArrayList<>(HOTBAR_SIZE);
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                hotbar.add(new HeldTool(slot, item.getType().name()));
            }
        }
        return hotbar;
    }
}
