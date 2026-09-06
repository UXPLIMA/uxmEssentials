package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised presentation of a browse menu, loaded once from {@code modules/<m>/gui/<name>.conf}: the
 * row count, the per-icon fallback {@link Material} (the icon for an itemless menu or an empty kit), the nav
 * button {@link Material}, the two reserved navigation slots, and the optional content-slot list. Titles and
 * lore stay in code as {@code MessageKey} lookups. This record holds layout integers and materials only,
 * never localised strings, so a translated catalog and an operator-edited layout never collide.
 *
 * <p>The record is immutable and validated at construction: rows are 1..6 (the chest-inventory bound uxmLib
 * enforces too), the nav slots are non-negative, and both materials are non-null. A view reads these values
 * straight onto the uxmLib builder; the {@link Material#matchMaterial} parse that produces them runs once at
 * load time, never on the open hot path.
 *
 * @param rows the menu row count, 1..6
 * @param fallbackIcon the per-icon material for an itemless menu or an empty-kit fallback
 * @param navIcon the material of the previous/next navigation buttons
 * @param prevSlot the slot the previous-page button is pinned to
 * @param nextSlot the slot the next-page button is pinned to
 * @param contentSlots the explicit page-content slots, or empty to use uxmLib's default (all but the bottom row)
 */
@NullMarked
public record GuiLayout(
        int rows, Material fallbackIcon, Material navIcon, int prevSlot, int nextSlot, List<Integer> contentSlots) {

    public GuiLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        Objects.requireNonNull(fallbackIcon, "fallbackIcon");
        Objects.requireNonNull(navIcon, "navIcon");
        if (prevSlot < 0) {
            throw new IllegalArgumentException("prevSlot must be >= 0, was " + prevSlot);
        }
        if (nextSlot < 0) {
            throw new IllegalArgumentException("nextSlot must be >= 0, was " + nextSlot);
        }
        Objects.requireNonNull(contentSlots, "contentSlots");
        contentSlots = List.copyOf(contentSlots);
    }

    /**
     * A paginated-menu default carrying today's hardcoded presentation, used when no conf is present so a
     * missing file reproduces the prior behaviour byte-for-byte. The nav buttons are arrows in the reserved
     * bottom row and the content slots fall through to uxmLib's default.
     */
    public static GuiLayout paginatedDefault(Material fallbackIcon) {
        return new GuiLayout(6, fallbackIcon, Material.ARROW, 48, 50, List.of());
    }

    /**
     * A storage-menu default for the throwaway/disposal window: only the row count is meaningful presentation,
     * so the icon, nav, and slot fields are placeholders the storage path never reads.
     */
    public static GuiLayout storageDefault(int rows) {
        return new GuiLayout(rows, Material.CHEST, Material.ARROW, 0, 1, List.of());
    }

    /** The explicit content slots if the conf set any, else empty so the caller uses uxmLib's default. */
    public Optional<List<Integer>> explicitContentSlots() {
        return contentSlots.isEmpty() ? Optional.empty() : Optional.of(contentSlots);
    }
}
