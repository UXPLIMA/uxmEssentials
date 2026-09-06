package com.uxplima.uxmessentials.teleport.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.HotspotChunk;

/**
 * The in-memory {@link BiomeHotspots} registry: a per-world biome → recently-seen-chunks map, bounded to a cap per
 * biome so it can never grow without limit as the world is explored. Each biome's chunk set is deduped (a chunk
 * seen twice is one entry) and evicts its oldest hotspot once the cap is reached, so it keeps a bounded, rolling
 * window of "where this biome has been seen lately".
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code perWorld} is a {@link ConcurrentHashMap} keyed by world uid; each
 * value is a {@link ConcurrentHashMap} of biome → {@link BoundedChunks}. A {@code BoundedChunks} guards its own
 * order/dedup/sample with an intrinsic lock. {@code record} runs on the region threads the chunk-load listener fires
 * on (many at once under Folia), and {@code sample} runs on the search's async threads, so the fine-grained lock keeps
 * each biome's window consistent without a global monitor. No I/O ever runs under the lock.
 */
public final class CappedBiomeHotspots implements BiomeHotspots {

    private final int maxPerBiome;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<BiomeName, BoundedChunks>> perWorld =
            new ConcurrentHashMap<>();

    public CappedBiomeHotspots(int maxPerBiome) {
        if (maxPerBiome < 1) {
            throw new IllegalArgumentException("maxPerBiome must be >= 1: " + maxPerBiome);
        }
        this.maxPerBiome = maxPerBiome;
    }

    @Override
    public void record(WorldRef world, BiomeName biome, int chunkX, int chunkZ) {
        java.util.Objects.requireNonNull(world, "world");
        java.util.Objects.requireNonNull(biome, "biome");
        perWorld.computeIfAbsent(world.uid(), id -> new ConcurrentHashMap<>())
                .computeIfAbsent(biome, b -> new BoundedChunks(maxPerBiome))
                .add(new HotspotChunk(chunkX, chunkZ));
    }

    @Override
    public Optional<HotspotChunk> sample(WorldRef world, BiomeName biome, RandomGenerator random) {
        java.util.Objects.requireNonNull(world, "world");
        java.util.Objects.requireNonNull(biome, "biome");
        java.util.Objects.requireNonNull(random, "random");
        ConcurrentHashMap<BiomeName, BoundedChunks> byBiome = perWorld.get(world.uid());
        if (byBiome == null) {
            return Optional.empty();
        }
        BoundedChunks chunks = byBiome.get(biome);
        return chunks == null ? Optional.empty() : chunks.sample(random);
    }

    /** A bounded, deduped, oldest-first-evicting window of chunk coordinates for one biome. */
    private static final class BoundedChunks {

        private final int cap;
        private final List<HotspotChunk> order = new ArrayList<>();
        private final Set<HotspotChunk> present = new HashSet<>();

        BoundedChunks(int cap) {
            this.cap = cap;
        }

        synchronized void add(HotspotChunk chunk) {
            if (!present.add(chunk)) {
                return; // already known, dedup by chunk coordinate
            }
            order.add(chunk);
            if (order.size() > cap) {
                present.remove(order.remove(0)); // evict the oldest so the window stays bounded
            }
        }

        synchronized Optional<HotspotChunk> sample(RandomGenerator random) {
            if (order.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(order.get(random.nextInt(order.size())));
        }
    }
}
