package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Picks the inventory icon a single entity type shows in the NPC type selector. Each mob is drawn as its own
 * spawn egg ({@code <TYPE>_SPAWN_EGG}) so the picker reads like the vanilla creative menu; the fake-player entry
 * has no spawn egg, so it falls back to a player head, and any type whose name has no matching spawn-egg material
 * (the display entities, or a type added in a future version) falls back to a plain egg rather than breaking the
 * selector. The mapping is by material name, so it needs no per-type table to maintain.
 */
final class NpcTypeIcon {

    private static final String SPAWN_EGG_SUFFIX = "_SPAWN_EGG";

    private NpcTypeIcon() {}

    /** The icon material for {@code type}: its spawn egg, a player head for the player, or an egg when neither fits. */
    static Material iconFor(EntityType type) {
        Objects.requireNonNull(type, "type");
        if (type == EntityType.PLAYER) {
            return Material.PLAYER_HEAD;
        }
        Material egg = Material.getMaterial(type.name() + SPAWN_EGG_SUFFIX);
        return egg != null ? egg : Material.EGG;
    }

    /**
     * The icon material for the stored uppercase entity-type name an NPC carries. A name that no longer maps to a
     * real Bukkit type (a removed type, a hand-edited typo) falls back to a player head, the same icon the default
     * fake-player NPC shows: rather than breaking the list.
     */
    static Material iconFor(String entityTypeName) {
        Objects.requireNonNull(entityTypeName, "entityTypeName");
        try {
            return iconFor(EntityType.valueOf(entityTypeName.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Material.PLAYER_HEAD;
        }
    }
}
