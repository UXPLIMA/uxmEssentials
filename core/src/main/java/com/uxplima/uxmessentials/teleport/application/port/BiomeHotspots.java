package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;
import java.util.random.RandomGenerator;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.HotspotChunk;

/**
 * The passive rare-biome registry: a bounded, per-world biome → sampled-chunk map that learns where rare biomes
 * are as the world is explored. The adapter's {@code ChunkLoadEvent} listener records each freshly loaded chunk's
 * biome once (reading the already-resident chunk. No extra chunk load), and a biome-targeted search samples a
 * known hotspot to bias its candidate points toward it, so a rare biome converges instead of the search
 * random-hammering the whole radius.
 *
 * <p>The registry is deliberately Bukkit-free. It deals only in {@link WorldRef}, {@link BiomeName}, and chunk
 * grid coordinates, so the pure sampler and the search stay testable without a live server. Every implementation
 * is bounded (a cap per biome and an eviction policy) so the map can never grow without limit as players explore.
 */
public interface BiomeHotspots {

    /**
     * Record that {@code biome} was seen in the chunk at {@code (chunkX, chunkZ)} of {@code world}. Cheap and
     * non-blocking: no I/O, no chunk load. Repeated records of the same chunk collapse to one entry, and the per-biome
     * store is capped so an old hotspot is evicted once the cap is reached.
     */
    void record(WorldRef world, BiomeName biome, int chunkX, int chunkZ);

    /**
     * A randomly chosen known hotspot chunk for {@code (world, biome)}, drawn with {@code random}, or empty when the
     * registry has learned none yet. The biome-targeted search biases toward this chunk with the configured weight.
     */
    Optional<HotspotChunk> sample(WorldRef world, BiomeName biome, RandomGenerator random);

    /** A registry that learns and returns nothing: wired when biome hotspot targeting is disabled. */
    BiomeHotspots NONE = new BiomeHotspots() {
        @Override
        public void record(WorldRef world, BiomeName biome, int chunkX, int chunkZ) {
            // Hotspot targeting is off: learn nothing, so the biome search falls back to uniform sampling.
        }

        @Override
        public Optional<HotspotChunk> sample(WorldRef world, BiomeName biome, RandomGenerator random) {
            return Optional.empty();
        }
    };
}
