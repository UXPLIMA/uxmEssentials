package com.uxplima.uxmessentials.survival.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The farm-assist crop → seed lookup: which planting item a player must spend to replant a harvested crop. It is the
 * pure mapping behind the "harvest and replant, consuming one matching seed" rule. The adapter resolves the crop and
 * seed names to Bukkit materials, but the mapping itself carries no Bukkit identity so it is unit-testable on plain
 * strings.
 *
 * <p>Only the crops that grow from a single planting item and mature through a block-age are mapped: the four common
 * farmland crops and nether wart. Wheat and beetroot replant from their dedicated seed item, carrot and potato replant
 * from the vegetable itself, and nether wart replants from nether wart, so every mapped crop resolves to a real,
 * placeable seed. A crop absent from the map (a stem, a cocoa pod) simply is not farm-assisted, and the adapter skips
 * it.
 */
public final class Crops {

    /** Crop block material name → the material of the item consumed to replant it. */
    private static final Map<String, String> CROP_TO_SEED = Map.of(
            "WHEAT", "WHEAT_SEEDS",
            "CARROTS", "CARROT",
            "POTATOES", "POTATO",
            "BEETROOTS", "BEETROOT_SEEDS",
            "NETHER_WART", "NETHER_WART");

    private Crops() {}

    /**
     * The planting item to spend to replant {@code crop}, or empty when the crop is not farm-assistable.
     *
     * @param crop the crop block material name (upper-case, e.g. {@code CARROTS})
     * @return the seed material name (e.g. {@code CARROT}), or empty when the crop has no known seed
     */
    public static Optional<String> seedFor(String crop) {
        Objects.requireNonNull(crop, "crop");
        return Optional.ofNullable(CROP_TO_SEED.get(crop));
    }
}
