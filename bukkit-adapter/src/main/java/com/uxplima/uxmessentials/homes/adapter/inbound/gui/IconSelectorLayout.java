package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised geometry and palette of the home-icon picker, loaded once from
 * {@code modules/homes/gui/icon-selector.conf}: the row count, the offered {@link Material} icons, the navigation
 * materials, and the four navigation slots (reset, previous, back, next). Titles and lore stay in code as
 * {@code MessageKey} lookups: this record holds layout integers and materials only, never localised strings.
 *
 * @param rows the menu row count, 1..6
 * @param icons the materials offered as home icons, paged through the content cells
 * @param navMaterial the previous/next button material
 * @param resetMaterial the clear-custom-icon button material
 * @param resetSlot the reset button slot
 * @param prevSlot the previous-page button slot
 * @param backSlot the back-to-action-menu button slot
 * @param nextSlot the next-page button slot
 */
@NullMarked
public record IconSelectorLayout(
        int rows,
        List<Material> icons,
        Material navMaterial,
        Material resetMaterial,
        int resetSlot,
        int prevSlot,
        int backSlot,
        int nextSlot) {

    public IconSelectorLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        Objects.requireNonNull(icons, "icons");
        if (icons.isEmpty()) {
            throw new IllegalArgumentException("icons must not be empty");
        }
        icons = List.copyOf(icons);
        Objects.requireNonNull(navMaterial, "navMaterial");
        Objects.requireNonNull(resetMaterial, "resetMaterial");
    }

    /** The bundled default, used when no conf is present so a missing file still opens the picker. */
    public static IconSelectorLayout codeDefault() {
        return new IconSelectorLayout(
                6,
                List.of(
                        Material.RED_BED,
                        Material.BLUE_BED,
                        Material.GREEN_BED,
                        Material.YELLOW_BED,
                        Material.WHITE_BED,
                        Material.BLACK_BED,
                        Material.PURPLE_BED,
                        Material.ORANGE_BED,
                        Material.GRASS_BLOCK,
                        Material.DIRT,
                        Material.STONE,
                        Material.COBBLESTONE,
                        Material.OAK_LOG,
                        Material.OAK_PLANKS,
                        Material.BRICKS,
                        Material.BOOKSHELF,
                        Material.CRAFTING_TABLE,
                        Material.FURNACE,
                        Material.CHEST,
                        Material.ENDER_CHEST,
                        Material.ANVIL,
                        Material.BEACON,
                        Material.ENCHANTING_TABLE,
                        Material.TORCH,
                        Material.DIAMOND_BLOCK,
                        Material.GOLD_BLOCK,
                        Material.IRON_BLOCK,
                        Material.EMERALD_BLOCK,
                        Material.NETHERITE_BLOCK,
                        Material.LAPIS_BLOCK,
                        Material.DIAMOND_SWORD,
                        Material.DIAMOND_PICKAXE,
                        Material.BOW,
                        Material.SHIELD,
                        Material.COMPASS,
                        Material.CLOCK,
                        Material.MAP,
                        Material.LANTERN,
                        Material.OAK_SAPLING,
                        Material.WHEAT,
                        Material.CAMPFIRE,
                        Material.CAKE),
                Material.ARROW,
                Material.BARRIER,
                45,
                48,
                49,
                50);
    }
}
