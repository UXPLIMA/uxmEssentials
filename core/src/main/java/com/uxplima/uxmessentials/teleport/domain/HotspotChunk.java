package com.uxplima.uxmessentials.teleport.domain;

/**
 * One learned rare-biome hotspot: the chunk coordinates where a biome was seen at least once. The passive
 * {@code BiomeHotspots} registry stores these as the biome-targeted search's sampling bias. When a biome has a
 * known hotspot, most samples are drawn near it so a rare biome converges instead of the search random-hammering
 * the whole world.
 *
 * <p>Only the chunk grid position is kept (not a full block position): a chunk is a coarse-enough "the biome is
 * roughly here" marker, and storing chunk coords keeps the registry small and the once-per-chunk dedup trivial.
 * The centre-block helpers give the sampler a concrete block to offset from.
 *
 * @param chunkX the chunk's x grid coordinate (block x >> 4)
 * @param chunkZ the chunk's z grid coordinate (block z >> 4)
 */
public record HotspotChunk(int chunkX, int chunkZ) {

    /** The block x of the chunk's centre column, the point the weighted sampler offsets around. */
    public int centerBlockX() {
        return (chunkX << 4) + 8;
    }

    /** The block z of the chunk's centre column, the point the weighted sampler offsets around. */
    public int centerBlockZ() {
        return (chunkZ << 4) + 8;
    }
}
