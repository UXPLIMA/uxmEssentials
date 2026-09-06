package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The nine virtual workstations the itemworld surface opens (docs/10-feature-modules.md §15.10) as a single
 * enum, each carrying its command literal, an optional alias, its permission node, and the display name
 * rendered into {@link com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey#WORKSTATION_OPENED}.
 * Modelling them as one enum keeps the nine commands one parameterised {@link WorkstationCommand} class rather
 * than nine near-identical files; the enum holds only immutable scalar data so the open behaviour lives in
 * {@link #open} as a {@code switch}, keeping the constants Error-Prone-clean.
 *
 * <p>Most stations open through a {@link MenuType} view created for the player with no backing block, the
 * non-deprecated {@code MenuType.Typed#create(HumanEntity)} path, so no real workstation is required. The
 * ender chest opens the player's own {@code getEnderChest()} inventory. {@link #open} must run on the player's
 * region thread.
 */
@NullMarked
public enum Workstation {
    ANVIL("anvil", null, "uxmessentials.workstation.anvil", "anvil"),
    WORKBENCH("workbench", "craft", "uxmessentials.workstation.workbench", "crafting table"),
    ENDERCHEST("enderchest", "echest", "uxmessentials.workstation.enderchest", "ender chest"),
    GRINDSTONE("grindstone", null, "uxmessentials.workstation.grindstone", "grindstone"),
    CARTOGRAPHY("cartography", null, "uxmessentials.workstation.cartography", "cartography table"),
    LOOM("loom", null, "uxmessentials.workstation.loom", "loom"),
    SMITHINGTABLE("smithingtable", null, "uxmessentials.workstation.smithingtable", "smithing table"),
    STONECUTTER("stonecutter", null, "uxmessentials.workstation.stonecutter", "stonecutter"),
    FURNACE("furnace", null, "uxmessentials.workstation.furnace", "furnace");

    private final String literal;
    private final @Nullable String alias;
    private final String permission;
    private final String displayName;

    Workstation(String literal, @Nullable String alias, String permission, String displayName) {
        this.literal = literal;
        this.alias = alias;
        this.permission = permission;
        this.displayName = displayName;
    }

    /** This station's command literal, public so the itemworld hub keys its launcher button slot/material by it. */
    public String literal() {
        return literal;
    }

    /** This station's permission node: public so the itemworld hub gates its launcher button by it. */
    public String permission() {
        return permission;
    }

    /** This station's display name: public so the itemworld hub renders its launcher button by it. */
    public String displayName() {
        return displayName;
    }

    List<String> aliases() {
        return alias == null ? List.of() : List.of(alias);
    }

    /** Open this station's inventory view for {@code player}. Must run on the player's region thread. */
    public void open(Player player) {
        switch (this) {
            case ANVIL -> player.openInventory(MenuType.ANVIL.create(player));
            case WORKBENCH -> player.openInventory(MenuType.CRAFTING.create(player));
            case ENDERCHEST -> player.openInventory(player.getEnderChest());
            case GRINDSTONE -> player.openInventory(MenuType.GRINDSTONE.create(player));
            case CARTOGRAPHY -> player.openInventory(MenuType.CARTOGRAPHY_TABLE.create(player));
            case LOOM -> player.openInventory(MenuType.LOOM.create(player));
            case SMITHINGTABLE -> player.openInventory(MenuType.SMITHING.create(player));
            case STONECUTTER -> player.openInventory(MenuType.STONECUTTER.create(player));
            case FURNACE -> player.openInventory(MenuType.FURNACE.create(player));
        }
    }
}
