package com.uxplima.uxmessentials.migration.convert.athelion.parse;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * A raw Athelion warp location, read from the Bukkit-serialised {@code loc} block in {@code data.yml}. Athelion stores a
 * warp's destination as a serialised {@link org.bukkit.Location org.bukkit.Location}. A nested map of a world <em>name</em>
 * plus the six coordinate fields, so this record carries only what the parser read: a bare world name (Athelion keeps no
 * world uid) and the coordinates. Resolving the name to a {@code WorldRef} is the mapper's job, exactly as the EssentialsX
 * and hologram sources split raw parse from world resolution.
 *
 * @param world the world name Athelion serialised
 * @param x world x coordinate
 * @param y world y coordinate
 * @param z world z coordinate
 * @param yaw horizontal look angle in degrees
 * @param pitch vertical look angle in degrees
 */
@NullMarked
public record AthelionLocation(String world, double x, double y, double z, float yaw, float pitch) {

    public AthelionLocation {
        Objects.requireNonNull(world, "world");
    }
}
