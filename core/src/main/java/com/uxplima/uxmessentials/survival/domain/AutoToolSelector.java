package com.uxplima.uxmessentials.survival.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The pure "best tool for a block, among the tools the player is holding" selection behind autotool. Given the block
 * being broken and the player's hotbar, it names the slot holding the strongest tool of the family that block needs
 * a diamond pickaxe over a wooden one for stone, an axe for a log, so the adapter can swap the held slot before the
 * break lands. Both the block → tool-family classification and the tier ranking are here, off any Bukkit type, so the
 * whole decision is unit-testable on material names.
 *
 * <p>Faithful to AutoTool's intent (switch to the block's proper tool) but without its NMS destroy-speed reflection:
 * the block's required {@link ToolType} is classified by material family, and among the player's matching tools the one
 * with the highest {@link #tier(String) tier} wins, netherite over diamond over iron over stone over gold over wood.
 * Ties resolve to the lowest slot, so the selection is stable. A block that needs no particular tool, or a hotbar with
 * no matching tool, yields {@link OptionalInt#empty()} and the adapter leaves the held slot alone.
 */
public final class AutoToolSelector {

    private static final Set<String> HOE_BLOCKS = Set.of(
            "HAY_BLOCK",
            "TARGET",
            "DRIED_KELP_BLOCK",
            "SPONGE",
            "WET_SPONGE",
            "MOSS_BLOCK",
            "MOSS_CARPET",
            "NETHER_WART_BLOCK",
            "WARPED_WART_BLOCK",
            "SHROOMLIGHT");

    private static final Set<String> AXE_BLOCKS = Set.of(
            "CRAFTING_TABLE",
            "BOOKSHELF",
            "CHEST",
            "TRAPPED_CHEST",
            "BARREL",
            "CARTOGRAPHY_TABLE",
            "FLETCHING_TABLE",
            "SMITHING_TABLE",
            "LOOM",
            "LECTERN",
            "COMPOSTER",
            "BEEHIVE",
            "BEE_NEST",
            "PUMPKIN",
            "CARVED_PUMPKIN",
            "JACK_O_LANTERN",
            "MELON",
            "LADDER",
            "BAMBOO_BLOCK");

    private static final Set<String> SHOVEL_BLOCKS = Set.of(
            "DIRT",
            "GRASS_BLOCK",
            "PODZOL",
            "MYCELIUM",
            "COARSE_DIRT",
            "ROOTED_DIRT",
            "DIRT_PATH",
            "GRASS_PATH",
            "FARMLAND",
            "SAND",
            "RED_SAND",
            "GRAVEL",
            "CLAY",
            "SOUL_SAND",
            "SOUL_SOIL",
            "MUD",
            "MUDDY_MANGROVE_ROOTS",
            "SNOW",
            "SNOW_BLOCK",
            "POWDER_SNOW");

    private static final List<String> PICKAXE_MARKERS = List.of(
            "STONE",
            "DEEPSLATE",
            "TUFF",
            "GRANITE",
            "DIORITE",
            "ANDESITE",
            "BASALT",
            "CALCITE",
            "AMETHYST",
            "NETHERRACK",
            "OBSIDIAN",
            "TERRACOTTA",
            "CONCRETE",
            "BRICK",
            "PRISMARINE",
            "PURPUR",
            "QUARTZ",
            "NETHERITE",
            "COPPER",
            "IRON_BLOCK",
            "GOLD_BLOCK",
            "DIAMOND_BLOCK",
            "EMERALD_BLOCK",
            "RAW_",
            "ANVIL",
            "FURNACE",
            "PISTON",
            "ICE");

    /** Tier prefixes ranked weakest to strongest; the index is the tool's harvest strength. */
    private static final List<String> TIERS =
            List.of("WOODEN_", "GOLDEN_", "STONE_", "IRON_", "DIAMOND_", "NETHERITE_");

    /**
     * The slot of the strongest hotbar tool of the family {@code blockMaterial} needs, or empty when the block needs no
     * particular tool or the player holds none of the right family.
     *
     * @param blockMaterial the material name of the block being broken (case-insensitive)
     * @param hotbar the player's hotbar tools, one entry per occupied slot
     * @return the winning slot, or empty when there is nothing better to switch to
     */
    public OptionalInt bestSlot(String blockMaterial, List<HeldTool> hotbar) {
        Objects.requireNonNull(blockMaterial, "blockMaterial");
        Objects.requireNonNull(hotbar, "hotbar");
        ToolType required = requiredToolFor(blockMaterial);
        if (required == ToolType.NONE) {
            return OptionalInt.empty();
        }
        int bestSlot = -1;
        int bestTier = Integer.MIN_VALUE;
        for (HeldTool tool : hotbar) {
            if (ToolType.ofItem(tool.material()) != required) {
                continue;
            }
            int tier = tier(tool.material());
            if (tier > bestTier || (tier == bestTier && tool.slot() < bestSlot)) {
                bestTier = tier;
                bestSlot = tool.slot();
            }
        }
        return bestSlot < 0 ? OptionalInt.empty() : OptionalInt.of(bestSlot);
    }

    /**
     * The tool family that best breaks {@code blockMaterial}, or {@link ToolType#NONE} when no tool matters.
     *
     * @param blockMaterial the block material name (case-insensitive)
     * @return the required family
     */
    public ToolType requiredToolFor(String blockMaterial) {
        Objects.requireNonNull(blockMaterial, "blockMaterial");
        String name = blockMaterial.toUpperCase(Locale.ROOT);
        if (name.endsWith("_LEAVES") || HOE_BLOCKS.contains(name)) {
            return ToolType.HOE;
        }
        if (name.equals("COBWEB")) {
            return ToolType.SWORD;
        }
        if (name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_PLANKS")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || AXE_BLOCKS.contains(name)) {
            return ToolType.AXE;
        }
        if (name.endsWith("_CONCRETE_POWDER") || SHOVEL_BLOCKS.contains(name)) {
            return ToolType.SHOVEL;
        }
        if (name.endsWith("_ORE")
                || name.equals("ANCIENT_DEBRIS")
                || PICKAXE_MARKERS.stream().anyMatch(name::contains)) {
            return ToolType.PICKAXE;
        }
        return ToolType.NONE;
    }

    /** The harvest strength of {@code toolMaterial}: higher is stronger; a tierless tool (shears) ranks lowest. */
    private static int tier(String toolMaterial) {
        String name = toolMaterial.toUpperCase(Locale.ROOT);
        for (int i = TIERS.size() - 1; i >= 0; i--) {
            if (name.startsWith(TIERS.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * One occupied hotbar slot: the slot index and the material name of the item in it.
     *
     * @param slot the hotbar slot index, at least zero
     * @param material the item material name
     */
    public record HeldTool(int slot, String material) {
        public HeldTool {
            Objects.requireNonNull(material, "material");
            if (slot < 0) {
                throw new IllegalArgumentException("slot must be non-negative: " + slot);
            }
        }
    }
}
