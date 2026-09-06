package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;

/**
 * The vanilla portal coordinate-scaling rule. Travelling between dimensions multiplies the
 * horizontal block coordinates so a journey in the nether covers eight times the distance of the
 * same move in the overworld: the nether runs at a coordinate scale of 8, every other environment at
 * 1. The Y coordinate is never scaled: vertical placement is the caller's concern.
 */
public final class PortalScaling {

    private PortalScaling() {}

    /**
     * The factor to multiply the source horizontal coordinates by when crossing from {@code from} to
     * {@code to}. Overworld to nether yields {@code 0.125}, nether to overworld {@code 8.0}, and any
     * pair sharing a coordinate scale yields {@code 1.0}.
     */
    public static double scale(WorldEnvironment from, WorldEnvironment to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return coordinateScale(from) / coordinateScale(to);
    }

    private static double coordinateScale(WorldEnvironment env) {
        return env == WorldEnvironment.NETHER ? 8.0 : 1.0;
    }
}
