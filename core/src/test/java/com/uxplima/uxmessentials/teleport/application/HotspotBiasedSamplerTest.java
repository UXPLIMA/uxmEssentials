package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.HotspotChunk;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import org.junit.jupiter.api.Test;

/**
 * The weighted hotspot sampler: for a biome-targeted area with a known hotspot, roughly {@code weight} of the samples
 * cluster near it and the rest are uniform; with no hotspot (or an untargeted area) every sample is a plain uniform
 * draw over the annulus. The assertions are seeded and statistical over a large sample.
 */
class HotspotBiasedSamplerTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final BiomeName DESERT = BiomeName.of("desert");
    private static final int HOTSPOT_RADIUS = 512;
    private static final int SAMPLES = 8_000;

    // A huge annulus centred at the origin so a hotspot offset always fits inside it and a uniform draw almost never
    // lands near the far-off hotspot chunk by chance.
    private static final SafeSearchArea BASE_AREA = new SafeSearchArea(WORLD, 0, 0, 0, 100_000, 100_000);
    private static final HotspotChunk HOTSPOT = new HotspotChunk(200, 200); // centre block (3208, 3208)

    @Test
    void aboutTheConfiguredWeightOfSamplesClusterNearTheHotspot() {
        HotspotBiasedSampler sampler = new HotspotBiasedSampler(fixedHotspots(HOTSPOT), 0.7, HOTSPOT_RADIUS);
        SafeSearchArea targeted = BASE_AREA.withTargetBiome(DESERT);
        RandomGenerator random = new Random(42);

        double nearFraction = fractionNearHotspot(sampler, targeted, random);

        assertThat(nearFraction).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.04));
    }

    @Test
    void withNoHotspotEverySampleIsUniform() {
        HotspotBiasedSampler sampler = new HotspotBiasedSampler(BiomeHotspots.NONE, 0.7, HOTSPOT_RADIUS);
        SafeSearchArea targeted = BASE_AREA.withTargetBiome(DESERT);
        RandomGenerator random = new Random(42);

        double nearFraction = fractionNearHotspot(sampler, targeted, random);

        // No hotspot is known, so the biased branch never bites: essentially nothing lands near the far chunk.
        assertThat(nearFraction).isLessThan(0.02);
    }

    @Test
    void anUntargetedAreaIsAlwaysUniformEvenWithAHotspot() {
        HotspotBiasedSampler sampler = new HotspotBiasedSampler(fixedHotspots(HOTSPOT), 0.7, HOTSPOT_RADIUS);
        RandomGenerator random = new Random(42);

        // BASE_AREA carries no target biome, so the hotspot bias is skipped entirely, the plain /rtp path.
        double nearFraction = fractionNearHotspot(sampler, BASE_AREA, random);

        assertThat(nearFraction).isLessThan(0.02);
    }

    @Test
    void everySampleStaysInsideTheAnnulus() {
        HotspotBiasedSampler sampler = new HotspotBiasedSampler(fixedHotspots(HOTSPOT), 0.7, HOTSPOT_RADIUS);
        SafeSearchArea targeted = BASE_AREA.withTargetBiome(DESERT);
        RandomGenerator random = new Random(3);

        for (int i = 0; i < SAMPLES; i++) {
            double[] point = sampler.sample(targeted, random);
            assertThat(targeted.contains(point[0], point[1])).isTrue();
        }
    }

    private static double fractionNearHotspot(
            HotspotBiasedSampler sampler, SafeSearchArea area, RandomGenerator random) {
        int near = 0;
        for (int i = 0; i < SAMPLES; i++) {
            double[] point = sampler.sample(area, random);
            double dx = point[0] - HOTSPOT.centerBlockX();
            double dz = point[1] - HOTSPOT.centerBlockZ();
            if (Math.sqrt((dx * dx) + (dz * dz)) <= HOTSPOT_RADIUS) {
                near++;
            }
        }
        return (double) near / SAMPLES;
    }

    private static BiomeHotspots fixedHotspots(HotspotChunk chunk) {
        return new BiomeHotspots() {
            @Override
            public void record(WorldRef world, BiomeName biome, int chunkX, int chunkZ) {}

            @Override
            public Optional<HotspotChunk> sample(WorldRef world, BiomeName biome, RandomGenerator random) {
                return Optional.of(chunk);
            }
        };
    }
}
