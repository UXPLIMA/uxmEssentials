package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Locale;

import org.bukkit.Material;

import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves the icon material for a warp category, shared across the category GUIs so the parsing-and-fallback
 * rule lives in one place. A category with no display material, or one naming a material this server does not
 * have, falls back to a book: the same default the kit category icons use.
 */
@NullMarked
final class WarpCategoryIcons {

    private static final Material DEFAULT = Material.BOOK;

    private WarpCategoryIcons() {}

    static Material material(WarpCategory category) {
        if (category.displayMaterial().isEmpty()) {
            return DEFAULT;
        }
        try {
            Material parsed = Material.valueOf(category.displayMaterial().get().toUpperCase(Locale.ROOT));
            return parsed.isAir() ? DEFAULT : parsed;
        } catch (IllegalArgumentException absent) {
            return DEFAULT;
        }
    }
}
