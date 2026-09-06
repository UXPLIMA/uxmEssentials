package com.uxplima.uxmessentials.teleport.domain;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * The bounded annulus a world's random-teleport candidates are drawn from: a centre, a minimum and
 * maximum radius, and the world-border radius the candidates must stay inside. The policy clamps the
 * effective maximum to the smaller of the configured radius and the border so an RTP never lands a
 * player outside the playable area.
 *
 * <p>An area may carry an optional {@link #targetBiome}: when set (the {@code /rtp biome <biome>} path), the
 * {@link SafeSearchPolicy} accepts a candidate only if it validated in that biome, in addition to the normal
 * safety/claim checks. An untargeted area (the plain {@code /rtp} refill) leaves it {@code null} and the biome
 * gate is skipped, so the ordinary path is unchanged.
 *
 * @param world the world this area describes
 * @param centerX the x coordinate candidates are measured from (usually the world spawn or 0)
 * @param centerZ the z coordinate candidates are measured from
 * @param minRadius the inner radius candidates must clear (0 for none)
 * @param configuredMaxRadius the operator's per-world outer radius
 * @param borderRadius the world-border radius from the centre, or {@link Double#MAX_VALUE} if unbounded
 * @param targetBiome the biome a candidate must land in, or {@code null} for an untargeted search
 */
public record SafeSearchArea(
        WorldRef world,
        double centerX,
        double centerZ,
        double minRadius,
        double configuredMaxRadius,
        double borderRadius,
        @Nullable BiomeName targetBiome) {

    public SafeSearchArea {
        Objects.requireNonNull(world, "world");
        requireFinite(centerX, "centerX");
        requireFinite(centerZ, "centerZ");
        if (minRadius < 0) {
            throw new IllegalArgumentException("minRadius must be >= 0: " + minRadius);
        }
        if (configuredMaxRadius < minRadius) {
            throw new IllegalArgumentException("configuredMaxRadius must be >= minRadius");
        }
        if (borderRadius < 0) {
            throw new IllegalArgumentException("borderRadius must be >= 0: " + borderRadius);
        }
    }

    /** An untargeted area, the plain {@code /rtp} refill, with no per-biome constraint. */
    public SafeSearchArea(
            WorldRef world,
            double centerX,
            double centerZ,
            double minRadius,
            double configuredMaxRadius,
            double borderRadius) {
        this(world, centerX, centerZ, minRadius, configuredMaxRadius, borderRadius, null);
    }

    /** A copy of this area constrained to {@code biome}, the {@code /rtp biome} search path. */
    public SafeSearchArea withTargetBiome(BiomeName biome) {
        Objects.requireNonNull(biome, "biome");
        return new SafeSearchArea(world, centerX, centerZ, minRadius, configuredMaxRadius, borderRadius, biome);
    }

    /**
     * A copy of this area whose configured outer radius is {@code newConfiguredMaxRadius}, floored at the inner
     * radius so the compact constructor's {@code maxRadius >= minRadius} invariant always holds. This is the
     * per-permission {@code uxmessentials.rtp.radius.<n>} tier's seam: the resolved radius becomes the player's
     * effective search bound, still clamped to the world border on serve by {@link #maxRadius()}.
     */
    public SafeSearchArea withConfiguredMaxRadius(double newConfiguredMaxRadius) {
        double clamped = Math.max(minRadius, newConfiguredMaxRadius);
        return new SafeSearchArea(world, centerX, centerZ, minRadius, clamped, borderRadius, targetBiome);
    }

    /** The biome a candidate must land in, when this is a biome-targeted search. */
    public Optional<BiomeName> targetBiomeName() {
        return Optional.ofNullable(targetBiome);
    }

    /** The effective outer radius: the configured radius clamped to the world border. */
    public double maxRadius() {
        return Math.min(configuredMaxRadius, borderRadius);
    }

    /** True when {@code (x, z)} falls within the {@code [minRadius, maxRadius]} annulus from the centre. */
    public boolean contains(double x, double z) {
        double r = radiusOf(x, z);
        return r >= minRadius && r <= maxRadius();
    }

    /** The horizontal distance of {@code (x, z)} from the centre. */
    public double radiusOf(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return Math.sqrt((dx * dx) + (dz * dz));
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite: " + value);
        }
    }
}
