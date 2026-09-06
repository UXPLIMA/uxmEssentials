package com.uxplima.uxmessentials.worlds.domain;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/** Encodes a world spawn as {@code x;y;z;yaw;pitch} for the {@code spawn} setting key (world implied). */
public final class SpawnCodec {

    private SpawnCodec() {}

    public static String encode(Position p) {
        return encode(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }

    /**
     * Encodes the five components on their own, for a caller that has coordinates but no world identity to build a
     * {@link Position} from: the world-import path, where the world's uid is not known until it is first loaded.
     */
    public static String encode(double x, double y, double z, float yaw, float pitch) {
        return x + ";" + y + ";" + z + ";" + yaw + ";" + pitch;
    }

    /** Parses the 5 numeric components {x,y,z,yaw,pitch} from an encoded spawn string (world-agnostic). */
    public static Optional<double[]> parseComponents(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String[] parts = raw.split(";", -1);
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            return Optional.of(new double[] {
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Double.parseDouble(parts[4])
            });
        } catch (NumberFormatException bad) {
            return Optional.empty();
        }
    }

    public static Optional<Position> decode(String raw, WorldRef world) {
        return parseComponents(raw).map(c -> new Position(world, c[0], c[1], c[2], (float) c[3], (float) c[4]));
    }
}
