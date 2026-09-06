package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * A pre-validated random-teleport destination held in a world's pre-warmed queue. It is a {@link
 * Position} that already passed the {@link SafeSearchPolicy} at {@link #validatedAt}, plus the cheap
 * in-memory facts ({@link #radius} from the world centre) needed to revalidate it lazily on serve when
 * the config radius shrank or the world border moved since it was queued.
 *
 * <p>{@link #biome} is the biome the column validated in, carried so the durable {@link RtpColumn} can persist
 * it (the P5 per-biome pool slice). It is {@code null} when the validating candidate carried no biome, the
 * on-serve revalidation never re-reads it, so an absent biome only means the column is not indexed per biome.
 *
 * @param position the safe landing position
 * @param radius the horizontal distance from the world's RTP centre, for stale-on-serve checks
 * @param biome the biome the location validated in, or {@code null} when it was not recorded
 * @param validatedAt when the location last passed the full off-thread validation
 */
public record RtpSafeLocation(
        Position position, double radius, @Nullable BiomeName biome, Instant validatedAt) {

    public RtpSafeLocation {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(validatedAt, "validatedAt");
        if (!Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("radius must be finite and non-negative: " + radius);
        }
    }

    /** A location with no recorded biome: the pre-P5 form, before the validated biome was threaded through. */
    public RtpSafeLocation(Position position, double radius, Instant validatedAt) {
        this(position, radius, null, validatedAt);
    }

    /** The biome the location validated in, when one was recorded. */
    public Optional<BiomeName> biomeName() {
        return Optional.ofNullable(biome);
    }

    /** The world this location belongs to: the queue is keyed per world. */
    public WorldRef world() {
        return position.world();
    }

    /**
     * The cheap revalidation a {@code poll()} runs before serving: a location whose radius now exceeds
     * the world's current maximum (the operator shrank the radius, or the border moved inward) is
     * discarded and the next is polled. Biome and chunk-safety are not re-read here: that is the
     * off-thread refill primitive's job; this is the O(1) on-serve guard only.
     */
    public boolean stillWithin(SafeSearchArea area) {
        Objects.requireNonNull(area, "area");
        return radius <= area.maxRadius();
    }
}
