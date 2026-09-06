package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised geometry of the {@code /home} slot grid, loaded once from
 * {@code modules/homes/gui/home-list.conf}: the row count, the cells homes are drawn into, the materials for a
 * filled-without-custom-icon slot, an empty (clickable to create) slot, and the border filler, plus the three
 * navigation slots. Titles and lore stay in code as {@code MessageKey} lookups. This record holds layout
 * integers and materials only, never localised strings.
 *
 * @param rows the menu row count, 1..6
 * @param homeSlots the cells a home is drawn into, in order; the count is the per-page capacity
 * @param fallbackIcon the material of a filled slot whose home set no custom icon
 * @param emptyIcon the material of an empty, create-on-click slot
 * @param filler the material the unused border cells are filled with
 * @param prevSlot the slot the previous-page button sits in
 * @param nextSlot the slot the next-page button sits in
 * @param pageInfoSlot the slot the page-indicator display sits in
 */
@NullMarked
public record HomeListLayout(
        int rows,
        List<Integer> homeSlots,
        Material fallbackIcon,
        Material emptyIcon,
        Material filler,
        int prevSlot,
        int nextSlot,
        int pageInfoSlot) {

    public HomeListLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        Objects.requireNonNull(homeSlots, "homeSlots");
        if (homeSlots.isEmpty()) {
            throw new IllegalArgumentException("homeSlots must not be empty");
        }
        homeSlots = List.copyOf(homeSlots);
        Objects.requireNonNull(fallbackIcon, "fallbackIcon");
        Objects.requireNonNull(emptyIcon, "emptyIcon");
        Objects.requireNonNull(filler, "filler");
    }

    /** The bundled default, used when no conf is present so a missing file still opens the grid. */
    public static HomeListLayout codeDefault() {
        return new HomeListLayout(
                3,
                List.of(10, 11, 12, 13, 14, 15, 16),
                Material.RED_BED,
                Material.GRAY_BED,
                Material.BLACK_STAINED_GLASS_PANE,
                18,
                26,
                22);
    }

    /** The number of home cells per page. */
    public int pageSize() {
        return homeSlots.size();
    }
}
