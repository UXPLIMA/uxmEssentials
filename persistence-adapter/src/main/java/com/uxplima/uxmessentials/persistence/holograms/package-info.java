/**
 * The holograms context's persistence adapter: the jOOQ {@code HologramRepository} over the generated
 * {@code HOLOGRAMS} and {@code HOLOGRAM_LINES} tables, the {@code HologramRows} anti-corruption mapping, the
 * Caffeine read-cache decorator, and the {@code HologramRepositories} factory the bukkit-adapter wires
 * through. The name row and its ordered line rows are both first-class, queryable columns, no opaque blob,
 * so a hologram round-trips losslessly and a single line can be edited in place.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.holograms;
