package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;

/**
 * One band of a flat world: a block type repeated {@code height} times (≥ 1), ordered bottom→top
 * within a {@link FlatLayerPlan}. Pure data. The adapter resolves {@link #block()} to a
 * {@code Material} and writes the band via {@code ChunkData.setRegion}.
 */
public record FlatLayer(BlockId block, int height) {

    public FlatLayer {
        Objects.requireNonNull(block, "block");
        if (height < 1) {
            throw new IllegalArgumentException("flat layer height must be at least 1: " + height);
        }
    }
}
