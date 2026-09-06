package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;

import org.jspecify.annotations.NullMarked;

/**
 * The anti-corruption boundary that resolves an itemworld domain's already-normalised mob-type id against the
 * live Paper {@link EntityType} registry, the entity-side counterpart of {@link BukkitItemResolver}. The
 * domain ({@code MobSpec}, {@code PurgeSelection}) owns the id <em>shape</em> (lowercase {@code namespace:path},
 * count/radius caps); this resolver owns the registry lookup, the one remaining failure mode a
 * well-formed-but-unknown type hits, mapped by the caller to {@code SPAWNMOB_UNKNOWN}.
 *
 * <p>Resolution is pure and side-effect-free: {@link #spawnable} returns empty for an unknown id or a
 * non-spawnable type ({@code UNKNOWN}, {@code PLAYER}), so a command renders the localized failure and stops
 * rather than handing an invalid type to the world.
 */
@NullMarked
public final class BukkitEntityResolver {

    private BukkitEntityResolver() {}

    /** The {@link EntityType} for {@code typeId}, present only when it resolves to a spawnable, living-or-mob type. */
    public static Optional<EntityType> spawnable(String typeId) {
        NamespacedKey key = NamespacedKey.fromString(typeId);
        if (key == null) {
            return Optional.empty();
        }
        EntityType type = Registry.ENTITY_TYPE.get(key);
        if (type == null || type == EntityType.UNKNOWN || type == EntityType.PLAYER || !type.isSpawnable()) {
            return Optional.empty();
        }
        return Optional.of(type);
    }
}
