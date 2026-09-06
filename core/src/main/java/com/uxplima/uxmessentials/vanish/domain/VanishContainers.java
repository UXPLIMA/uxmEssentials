package com.uxplima.uxmessentials.vanish.domain;

import java.util.Objects;
import java.util.Set;

/**
 * The pure classification of which container types broadcast an <em>open animation and sound</em> to nearby players
 * the lidded blocks (chests, ender chests, shulker boxes, barrels) whose viewer count drives a block action every
 * onlooker sees and hears. A vanished player's access to one of these must be silenced so the open never betrays them;
 * a furnace, hopper, dispenser, anvil, or workbench animates nothing, so opening one is already silent and needs no
 * special handling.
 *
 * <p>The knowledge lives here, in the domain, as a small set keyed by the Bukkit {@code InventoryType} name (the
 * adapter passes {@code inventoryType.name()}), so the "is this container noisy?" decision is one pure, testable rule
 * rather than an inline literal set in a listener. The domain never imports the Bukkit enum: it compares names.
 */
public final class VanishContainers {

    /**
     * The {@code InventoryType} names whose open increments a block's viewer count and fires a lid animation plus an
     * open sound to every nearby player. A trapped chest and a double chest both report {@code CHEST}.
     */
    private static final Set<String> ANIMATED_OPEN = Set.of("CHEST", "ENDER_CHEST", "SHULKER_BOX", "BARREL");

    private VanishContainers() {}

    /**
     * Whether a container of the given Bukkit {@code InventoryType} name broadcasts an open animation and sound to
     * onlookers, so a vanished opener must have it silenced. A {@code null} or unknown type is treated as silent.
     */
    public static boolean broadcastsOpen(String inventoryTypeName) {
        Objects.requireNonNull(inventoryTypeName, "inventoryTypeName");
        return ANIMATED_OPEN.contains(inventoryTypeName);
    }
}
