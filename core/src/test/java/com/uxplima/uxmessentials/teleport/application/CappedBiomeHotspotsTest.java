package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.HotspotChunk;
import org.junit.jupiter.api.Test;

/**
 * The bounded rare-biome registry: a recorded chunk is sampleable, the per-biome window is capped and evicts its
 * oldest hotspot, and an unknown world/biome samples nothing.
 */
class CappedBiomeHotspotsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef OTHER = new WorldRef(UUID.randomUUID(), "world_nether");
    private static final BiomeName DESERT = BiomeName.of("desert");

    @Test
    void aRecordedChunkIsSampledBack() {
        CappedBiomeHotspots registry = new CappedBiomeHotspots(30);
        registry.record(WORLD, DESERT, 10, 20);

        Optional<HotspotChunk> sampled = registry.sample(WORLD, DESERT, new Random(1));

        assertThat(sampled).contains(new HotspotChunk(10, 20));
    }

    @Test
    void theWindowIsBoundedAndEvictsTheOldest() {
        CappedBiomeHotspots registry = new CappedBiomeHotspots(2);
        registry.record(WORLD, DESERT, 0, 0);
        registry.record(WORLD, DESERT, 1, 1);
        registry.record(WORLD, DESERT, 2, 2); // overflows the cap of 2, evicting the oldest (0, 0)

        Set<HotspotChunk> sampled = new HashSet<>();
        Random random = new Random(7);
        for (int i = 0; i < 400; i++) {
            registry.sample(WORLD, DESERT, random).ifPresent(sampled::add);
        }

        // The two newest survive; the oldest was evicted so it can never be sampled: the window stays bounded.
        assertThat(sampled).containsExactlyInAnyOrder(new HotspotChunk(1, 1), new HotspotChunk(2, 2));
    }

    @Test
    void anUnknownBiomeOrWorldSamplesNothing() {
        CappedBiomeHotspots registry = new CappedBiomeHotspots(30);
        registry.record(WORLD, DESERT, 5, 5);

        assertThat(registry.sample(WORLD, BiomeName.of("jungle"), new Random(1)))
                .isEmpty();
        assertThat(registry.sample(OTHER, DESERT, new Random(1))).isEmpty();
    }

    @Test
    void aDisabledRegistryLearnsAndReturnsNothing() {
        com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots none =
                com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots.NONE;
        none.record(WORLD, DESERT, 1, 1);

        assertThat(none.sample(WORLD, DESERT, new Random(1))).isEmpty();
    }
}
