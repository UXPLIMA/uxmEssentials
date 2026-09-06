package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * A raw EssentialsX location as it appears in a {@code userdata} or {@code warps} YAML file: a world
 * name and the six coordinate fields. This is source-shaped foreign detail. The world is a bare name
 * (EssentialsX stores no world uid), so resolving it to a {@code WorldRef} is the mapper's job. Nothing
 * here is a domain type; it only carries what the parser read.
 *
 * @param world the EssentialsX world name
 * @param x world x coordinate
 * @param y world y coordinate
 * @param z world z coordinate
 * @param yaw horizontal look angle in degrees
 * @param pitch vertical look angle in degrees
 */
@NullMarked
public record EssXLocation(String world, double x, double y, double z, float yaw, float pitch) {

    public EssXLocation {
        Objects.requireNonNull(world, "world");
    }
}
