/**
 * The npc context's persistence adapter: the jOOQ {@code NpcRepository} over the generated {@code NPC} table,
 * the {@code NpcRows} anti-corruption mapping, the Caffeine read-cache decorator, and the {@code NpcRepositories}
 * factory the bukkit-adapter wires through. Every NPC fact. Name, world, coordinates, look direction, skin
 * texture/signature, click command, creation time. Is a first-class, queryable column (no opaque blob), so an
 * NPC round-trips losslessly and survives a restart or a world rollback.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.npc;
