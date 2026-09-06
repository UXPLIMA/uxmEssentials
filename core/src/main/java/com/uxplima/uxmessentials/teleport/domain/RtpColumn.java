package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * One persisted random-teleport pool entry: a world plus the block {@code x}/{@code z} of a column that
 * passed the {@link SafeSearchPolicy} at {@link #validatedAt}, and the biome it validated in. It is the
 * durable, restart-surviving form of a queued location, but deliberately thinner than {@link
 * RtpSafeLocation}, because only the horizontal column is trusted across a restart.
 *
 * <p>The landing Y is <strong>not</strong> stored: the world may have changed since the column was
 * validated (a rollback, a regenerated chunk, a border move), so on pre-warm the column is re-probed
 * a fresh Y is resolved and the full safety check re-run off an async chunk read: before it re-enters
 * the servable queue. Persisting X/Z and recomputing Y live is what makes a restart pre-warm one cheap,
 * high-hit-rate probe per column instead of a cold 40-attempt search.
 *
 * <p>{@link #biome} is nullable and reserved for the P5 per-biome pool: it is written now (from the
 * validated candidate, when known) so the schema and this value carry it forward, and read there.
 *
 * @param world the world the column belongs to. The pool is keyed per world
 * @param x the column's block x coordinate
 * @param z the column's block z coordinate
 * @param biome the biome the column validated in, or {@code null} when it was not recorded
 * @param validatedAt when the column last passed the full off-thread validation
 */
public record RtpColumn(
        WorldRef world, int x, int z, @Nullable BiomeName biome, Instant validatedAt) {

    public RtpColumn {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(validatedAt, "validatedAt");
    }

    /** A column with no recorded biome, the common P2 case, since the servable location carries none yet. */
    public RtpColumn(WorldRef world, int x, int z, Instant validatedAt) {
        this(world, x, z, null, validatedAt);
    }

    /**
     * The durable column form of a servable {@link RtpSafeLocation}: its world and block x/z with the
     * validation instant and the biome it validated in, dropping the resolved Y (recomputed live on pre-warm)
     * and the radius (an on-serve guard, not persisted). The biome is threaded through so the persisted pool can
     * be sliced per biome (the P5 {@code /rtp biome} path); it is {@code null} only when the location carried none.
     */
    public static RtpColumn of(RtpSafeLocation location) {
        Objects.requireNonNull(location, "location");
        return new RtpColumn(
                location.world(),
                location.position().blockX(),
                location.position().blockZ(),
                location.biome(),
                location.validatedAt());
    }

    /** The recorded biome, when one was stored. */
    public Optional<BiomeName> biomeName() {
        return Optional.ofNullable(biome);
    }
}
