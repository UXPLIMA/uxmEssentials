package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import org.jspecify.annotations.Nullable;

/**
 * Renders one of the viewer's currently-equipped items as a menu icon. The spec is an exact keyword, matched
 * case-insensitively: {@code main_hand}/{@code mainhand}, {@code off_hand}/{@code offhand}, {@code helmet},
 * {@code chestplate}, {@code leggings} or {@code boots}. A keyword that is not one of these is not claimed (so a
 * bare material name is left for the material fallback).
 *
 * <p>The provider reads only the <em>viewer's own</em> inventory, resolved from their UUID on the calling
 * thread. The menu's item-build chain already runs on the viewer's entity thread, so this is Folia-safe and
 * never blocks the main thread. The matching slot is returned as a clone, so layering name/lore onto the icon
 * can never mutate the player's real item. An empty or AIR slot, or a viewer who is offline, yields
 * {@link Optional#empty()} so the spec falls through to the material fallback rather than rendering nothing.
 */
final class EquipmentIconProvider implements IconProvider {

    @Override
    public Optional<ItemStack> icon(String spec, MenuContext ctx) {
        Player viewer = Bukkit.getPlayer(ctx.viewer().uuid());
        if (viewer == null) {
            return Optional.empty();
        }
        return nonEmpty(equipped(viewer.getInventory(), spec.trim().toLowerCase(Locale.ROOT)));
    }

    /** The equipped item for {@code keyword}, or null when the keyword is not an equipment slot we render. */
    @Nullable private ItemStack equipped(PlayerInventory inventory, String keyword) {
        return switch (keyword) {
            case "main_hand", "mainhand" -> inventory.getItemInMainHand();
            case "off_hand", "offhand" -> inventory.getItemInOffHand();
            case "helmet" -> inventory.getHelmet();
            case "chestplate" -> inventory.getChestplate();
            case "leggings" -> inventory.getLeggings();
            case "boots" -> inventory.getBoots();
            default -> null;
        };
    }

    /** A clone of {@code item} when the slot actually holds something, else empty (null or AIR → fall through). */
    private Optional<ItemStack> nonEmpty(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        return Optional.of(item.clone());
    }
}
