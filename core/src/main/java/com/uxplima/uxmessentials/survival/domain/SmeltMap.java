package com.uxplima.uxmessentials.survival.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The autosmelt raw → smelted material mapping: which item a broken block's drop is transformed into before it reaches
 * the player (raw iron becomes an iron ingot, cobblestone becomes stone). It is the pure lookup behind "breaking a
 * smeltable ore yields the smelted result instead of the raw ore". The adapter resolves the drop and result names to
 * Bukkit materials and rebuilds the {@code ItemStack}, but the mapping itself carries no Bukkit identity so it is
 * unit-testable on plain strings.
 *
 * <p>The keys are the <em>drop</em> materials, not the block materials: in modern Minecraft an iron ore block drops
 * {@code RAW_IRON}, so the shipped map keys on {@code RAW_IRON} and not {@code IRON_ORE}. Keys and values are
 * normalised to upper case so a config that writes {@code raw_iron} still resolves. A drop with no entry is left
 * untouched, which is what lets autosmelt compose cleanly with autopickup. The pipeline smelts what it can and passes
 * the rest through.
 *
 * @param mapping the drop material name → smelted result material name pairs
 */
public record SmeltMap(Map<String, String> mapping) {

    public SmeltMap {
        Objects.requireNonNull(mapping, "mapping");
        Map<String, String> normalised = new LinkedHashMap<>();
        mapping.forEach((raw, smelted) -> {
            Objects.requireNonNull(raw, "raw material");
            Objects.requireNonNull(smelted, "smelted material");
            normalised.put(raw.toUpperCase(Locale.ROOT), smelted.toUpperCase(Locale.ROOT));
        });
        mapping = Map.copyOf(normalised);
    }

    /**
     * The smelted result for {@code dropMaterial}, or empty when the drop is not smeltable and passes through unchanged.
     *
     * @param dropMaterial the material name of a broken block's drop (case-insensitive, e.g. {@code RAW_IRON})
     * @return the result material name (e.g. {@code IRON_INGOT}), or empty when no mapping applies
     */
    public Optional<String> smelted(String dropMaterial) {
        Objects.requireNonNull(dropMaterial, "dropMaterial");
        return Optional.ofNullable(mapping.get(dropMaterial.toUpperCase(Locale.ROOT)));
    }

    /** Whether the map carries no entries, so autosmelt has nothing to transform. */
    public boolean isEmpty() {
        return mapping.isEmpty();
    }
}
