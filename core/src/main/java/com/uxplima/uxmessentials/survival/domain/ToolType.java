package com.uxplima.uxmessentials.survival.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * The family of a hand tool, used by autotool to match a held item to the block being broken. It is derived purely
 * from a Bukkit material <em>name</em> ({@code DIAMOND_PICKAXE} → {@link #PICKAXE}), so the classification carries no
 * Bukkit identity and is unit-testable on plain strings; the adapter passes {@code Material.name()} and never a live
 * {@code Material}.
 *
 * <p>{@link #NONE} covers everything that is not one of the six vanilla tool families. A block, a sword-less weapon,
 * an empty hand, so a hotbar slot that classifies to {@code NONE} is simply not a candidate for auto-selection.
 */
public enum ToolType {
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SHEARS,
    SWORD,
    NONE;

    /**
     * The tool family of the item named {@code material}, or {@link #NONE} when it is not a tool.
     *
     * @param material the item material name (case-insensitive, e.g. {@code NETHERITE_AXE})
     * @return the matching family, or {@link #NONE}
     */
    public static ToolType ofItem(String material) {
        Objects.requireNonNull(material, "material");
        String name = material.toUpperCase(Locale.ROOT);
        if (name.endsWith("_PICKAXE")) {
            return PICKAXE;
        }
        if (name.endsWith("_AXE")) {
            return AXE;
        }
        if (name.endsWith("_SHOVEL")) {
            return SHOVEL;
        }
        if (name.endsWith("_HOE")) {
            return HOE;
        }
        if (name.endsWith("_SWORD")) {
            return SWORD;
        }
        if (name.equals("SHEARS")) {
            return SHEARS;
        }
        return NONE;
    }
}
