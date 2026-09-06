package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised presentation of a fixed-action menu (one with a known set of buttons at fixed slots, not a
 * paginated list): the row count, the filler {@link Material} for the empty background, and a per-element slot
 * and {@link Material} keyed by a stable element name (e.g. {@code "deposit"}, {@code "close"}). Titles, button
 * labels, and lore stay in the message catalog as {@code MessageKey} lookups. This record holds layout
 * integers and materials only, never localised strings, so a translated catalog and an operator-edited layout
 * never collide.
 *
 * <p>Immutable and built once at load time from {@code modules/<m>/gui/<name>.conf}; the
 * {@link Material#matchMaterial} parse that produces the materials runs there, never on the menu's open hot
 * path. A view reads {@link #slot} and {@link #material} for each named element it places. Construction starts
 * from a code default carrying today's hardcoded geometry, so a missing or partial conf reproduces the prior
 * behaviour and an unknown name on the conf side is simply ignored.
 */
@NullMarked
public record FixedMenuLayout(
        int rows, Material fillerMaterial, Map<String, Integer> slots, Map<String, Material> materials) {

    public FixedMenuLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        Objects.requireNonNull(fillerMaterial, "fillerMaterial");
        slots = Map.copyOf(Objects.requireNonNull(slots, "slots"));
        materials = Map.copyOf(Objects.requireNonNull(materials, "materials"));
    }

    /** The slot the named element sits in; the {@code codeDefault} supplied it, so it is always present. */
    public int slot(String element) {
        Integer value = slots.get(Objects.requireNonNull(element, "element"));
        if (value == null) {
            throw new IllegalArgumentException("no slot configured for element " + element);
        }
        return value;
    }

    /** The material of the named element; the {@code codeDefault} supplied it, so it is always present. */
    public Material material(String element) {
        Material value = materials.get(Objects.requireNonNull(element, "element"));
        if (value == null) {
            throw new IllegalArgumentException("no material configured for element " + element);
        }
        return value;
    }

    /** Start a code-default builder with {@code rows} and a filler material; add elements with {@link Builder#element}. */
    public static Builder builder(int rows, Material fillerMaterial) {
        return new Builder(rows, fillerMaterial);
    }

    /** Assembles the code-default layout a view hands the loader as the fallback for a missing or partial conf. */
    public static final class Builder {

        private final int rows;
        private final Material fillerMaterial;
        private final Map<String, Integer> slots = new LinkedHashMap<>();
        private final Map<String, Material> materials = new LinkedHashMap<>();

        private Builder(int rows, Material fillerMaterial) {
            this.rows = rows;
            this.fillerMaterial = Objects.requireNonNull(fillerMaterial, "fillerMaterial");
        }

        /** Declare an element with its default slot and material; the conf may override either. */
        public Builder element(String name, int slot, Material material) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(material, "material");
            slots.put(name, slot);
            materials.put(name, material);
            return this;
        }

        /** Declare a slot-only element (no icon material, e.g. a label slot the view fills itself). */
        public Builder slotOnly(String name, int slot) {
            slots.put(Objects.requireNonNull(name, "name"), slot);
            return this;
        }

        public FixedMenuLayout build() {
            return new FixedMenuLayout(rows, fillerMaterial, slots, materials);
        }
    }
}
