package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The tree-feller replant lookup: which sapling belongs to a felled log, and which ground a sapling may be planted
 * on. Only the overworld species that grow from a placeable sapling are mapped. Mangrove replants its propagule, and
 * the nether stems (crimson/warped) have no sapling, so felling them simply leaves the base empty.
 */
@NullMarked
final class Saplings {

    private static final Map<Material, Material> LOG_TO_SAPLING = Map.ofEntries(
            Map.entry(Material.OAK_LOG, Material.OAK_SAPLING),
            Map.entry(Material.SPRUCE_LOG, Material.SPRUCE_SAPLING),
            Map.entry(Material.BIRCH_LOG, Material.BIRCH_SAPLING),
            Map.entry(Material.JUNGLE_LOG, Material.JUNGLE_SAPLING),
            Map.entry(Material.ACACIA_LOG, Material.ACACIA_SAPLING),
            Map.entry(Material.DARK_OAK_LOG, Material.DARK_OAK_SAPLING),
            Map.entry(Material.CHERRY_LOG, Material.CHERRY_SAPLING),
            Map.entry(Material.PALE_OAK_LOG, Material.PALE_OAK_SAPLING),
            Map.entry(Material.MANGROVE_LOG, Material.MANGROVE_PROPAGULE));

    /** The soils a sapling may be replanted on, so felling over stone or sand leaves the base empty. */
    private static final Set<Material> SOILS = Set.of(
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.MOSS_BLOCK,
            Material.MYCELIUM,
            Material.MUD,
            Material.MUDDY_MANGROVE_ROOTS);

    private Saplings() {}

    /** The sapling to replant for a felled {@code log}, or empty when the species has no plantable sapling. */
    static Optional<Material> forLog(Material log) {
        return Optional.ofNullable(LOG_TO_SAPLING.get(log));
    }

    /** Whether a sapling can be planted on {@code ground}. */
    static boolean isSoil(Material ground) {
        return SOILS.contains(ground);
    }
}
