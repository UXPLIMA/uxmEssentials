package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.HotspotChunk;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;

/**
 * The random-point sampler the safe-search draws candidate columns from. For an ordinary (untargeted) search it
 * samples uniformly over the world's annulus, {@code r = sqrt(rand·(max² − min²) + min²)} with an independent
 * uniform angle, the classic disc-uniform draw that avoids clumping toward the centre. For a biome-targeted search
 * ({@code /rtp biome <biome>}) it biases toward a learned {@link BiomeHotspots hotspot}: with probability {@code
 * hotspotWeight} it draws a point near a known hotspot chunk, otherwise (and whenever no hotspot is known yet) it
 * falls back to the same uniform draw. Biasing is what makes a rare biome converge instead of the search
 * random-hammering the whole radius, while the {@code 1 − weight} uniform share keeps the search from getting
 * stuck if the hotspots are all now unsuitable.
 *
 * <p>The sampler is pure: it takes the {@link RandomGenerator} on each call rather than owning one, so a test can
 * drive it with a seeded generator and assert the clustering statistically. Every point it returns is inside the
 * area's {@code [minRadius, maxRadius]} annulus. A hotspot offset that would fall outside the band is rejected and
 * the uniform draw is used instead.
 */
public final class HotspotBiasedSampler {

    private final BiomeHotspots hotspots;
    private final double hotspotWeight;
    private final int hotspotRadius;

    public HotspotBiasedSampler(BiomeHotspots hotspots, double hotspotWeight, int hotspotRadius) {
        this.hotspots = Objects.requireNonNull(hotspots, "hotspots");
        if (hotspotWeight < 0.0 || hotspotWeight > 1.0) {
            throw new IllegalArgumentException("hotspotWeight must be within [0, 1]: " + hotspotWeight);
        }
        if (hotspotRadius < 1) {
            throw new IllegalArgumentException("hotspotRadius must be >= 1: " + hotspotRadius);
        }
        this.hotspotWeight = hotspotWeight;
        this.hotspotRadius = hotspotRadius;
    }

    /** Sample one candidate {@code (x, z)} for {@code area}, hotspot-biased for a biome-targeted area, else uniform. */
    public double[] sample(SafeSearchArea area, RandomGenerator random) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(random, "random");
        Optional<BiomeName> target = area.targetBiomeName();
        if (target.isPresent() && hotspotWeight > 0.0 && random.nextDouble() < hotspotWeight) {
            Optional<double[]> nearHotspot = sampleNearHotspot(area, target.get(), random);
            if (nearHotspot.isPresent()) {
                return nearHotspot.get();
            }
        }
        return uniform(area, random);
    }

    private Optional<double[]> sampleNearHotspot(SafeSearchArea area, BiomeName biome, RandomGenerator random) {
        Optional<HotspotChunk> hotspot = hotspots.sample(area.world(), biome, random);
        if (hotspot.isEmpty()) {
            return Optional.empty();
        }
        HotspotChunk chunk = hotspot.get();
        double offset = random.nextDouble() * hotspotRadius;
        double angle = random.nextDouble(0, 2 * Math.PI);
        double x = chunk.centerBlockX() + (offset * Math.cos(angle));
        double z = chunk.centerBlockZ() + (offset * Math.sin(angle));
        return area.contains(x, z) ? Optional.of(new double[] {x, z}) : Optional.empty();
    }

    private static double[] uniform(SafeSearchArea area, RandomGenerator random) {
        double min = area.minRadius();
        double max = area.maxRadius();
        double angle = random.nextDouble(0, 2 * Math.PI);
        double minSq = min * min;
        double maxSq = max * max;
        double radius = maxSq <= minSq ? min : Math.sqrt(random.nextDouble(minSq, maxSq));
        double x = area.centerX() + (radius * Math.cos(angle));
        double z = area.centerZ() + (radius * Math.sin(angle));
        return new double[] {x, z};
    }
}
