package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * The native "special" item keywords a material name cannot express, because the item is a base material carrying
 * a specific data value rather than a distinct material:
 *
 * <ul>
 *   <li>{@code water_bottle}, a {@link Material#POTION} whose base potion type is {@link PotionType#WATER}, i.e.
 *       a plain water bottle (there is no {@code WATER_BOTTLE} material).
 *   <li>{@code light:<0-15>}, a {@link Material#LIGHT} block item with its light-block level set; the level is
 *       clamped to {@code 0..15} and a malformed or absent level falls back to the full {@code 15}.
 * </ul>
 *
 * <p>Both are best-effort: a runtime that cannot model the base potion type or block-data level still yields a
 * plain {@code POTION}/{@code LIGHT} item rather than throwing, the same fail-soft contract the rest of the render
 * pipeline keeps. The provider claims only these two keywords, never a bare material name.
 */
final class SpecialItemIconProvider implements IconProvider {

    private static final String WATER_BOTTLE = "water_bottle";
    private static final String LIGHT = "light:";
    private static final int MAX_LIGHT = 15;

    @Override
    public Optional<ItemStack> icon(String spec, MenuContext ctx) {
        String lower = spec.trim().toLowerCase(Locale.ROOT);
        if (lower.equals(WATER_BOTTLE)) {
            return Optional.of(waterBottle());
        }
        if (lower.startsWith(LIGHT)) {
            return Optional.of(light(lower.substring(LIGHT.length())));
        }
        return Optional.empty();
    }

    /** A plain water bottle: a POTION whose base type is WATER, degrading to a bare POTION if that is unmodelled. */
    private ItemStack waterBottle() {
        ItemStack potion = new ItemStack(Material.POTION);
        try {
            potion.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.WATER));
        } catch (RuntimeException unsupported) {
            // A runtime that cannot model the base potion type still yields a plain water-less POTION, never throws.
        }
        return potion;
    }

    /** A LIGHT block item at the parsed level (clamped 0..15, default 15), degrading to a bare LIGHT if unmodelled. */
    private ItemStack light(String levelToken) {
        ItemStack light = new ItemStack(Material.LIGHT);
        int level = clampLevel(levelToken);
        try {
            light.editMeta(BlockDataMeta.class, meta -> {
                BlockData data = Material.LIGHT.createBlockData();
                if (data instanceof Levelled levelled) {
                    levelled.setLevel(level);
                    meta.setBlockData(levelled);
                }
            });
        } catch (RuntimeException unsupported) {
            // A runtime that cannot model block-data light levels still yields a plain LIGHT item, never throws.
        }
        return light;
    }

    /** Parse the level token, clamping to {@code 0..15}; a malformed or out-of-range value falls back to full 15. */
    private static int clampLevel(String token) {
        try {
            return Math.max(0, Math.min(MAX_LIGHT, Integer.parseInt(token.trim())));
        } catch (NumberFormatException notANumber) {
            return MAX_LIGHT;
        }
    }
}
