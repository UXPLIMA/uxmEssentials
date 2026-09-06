package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of how a world is created, its environment, generation preset, optional
 * seed, optional external generator, structure toggle, and optional datapack dimension. Separated
 * from live mutable state (added by sub-project B) so clone/regenerate can copy the spec verbatim.
 */
public record WorldSpec(
        WorldEnvironment environment,
        WorldGenType worldType,
        Optional<Long> seed,
        Optional<GeneratorRef> generator,
        boolean generateStructures,
        Optional<DimensionKey> dimension) {

    public WorldSpec {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(worldType, "worldType");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(dimension, "dimension");
    }

    /** A plain normal-environment, vanilla-generation world with structures on and no seed. */
    public static WorldSpec normal() {
        return new WorldSpec(
                WorldEnvironment.NORMAL,
                WorldGenType.NORMAL,
                Optional.empty(),
                Optional.empty(),
                true,
                Optional.empty());
    }
}
