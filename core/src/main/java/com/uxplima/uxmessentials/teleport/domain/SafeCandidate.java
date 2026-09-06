package com.uxplima.uxmessentials.teleport.domain;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * The off-thread-read facts about one random-teleport candidate, handed to {@link SafeSearchPolicy} for
 * a pure accept/reject verdict. The adapter loads the destination chunk on the candidate's region
 * thread, reads its biome, the safety of the resolved Y, and the material the player would stand on, and
 * fills this record; the domain decides nothing about chunk loading, only whether the facts pass the
 * policy.
 *
 * @param position the candidate landing position (with a safe-Y already resolved by the adapter)
 * @param biome the biome at the candidate, for the excluded-biome check
 * @param standingSafe whether the resolved column has solid ground and breathable space (adapter-read)
 * @param insideClaim whether the candidate falls on protected land, inside a land claim or a WorldGuard
 *     region (adapter-read on the region thread; false when neither is present, or when the matching
 *     {@code respect-*} toggle is off)
 * @param landingBlock the material of the block the player would stand on, for the avoid-blocks check, or
 *     {@code null} when the adapter could not read it
 */
public record SafeCandidate(
        Position position,
        BiomeName biome,
        boolean standingSafe,
        boolean insideClaim,
        @Nullable BlockTypeName landingBlock) {

    public SafeCandidate {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(biome, "biome");
    }

    /** A candidate with no read landing material: the avoid-blocks check passes by default. */
    public SafeCandidate(Position position, BiomeName biome, boolean standingSafe, boolean insideClaim) {
        this(position, biome, standingSafe, insideClaim, null);
    }

    /** The candidate's x coordinate, for the radius/border check. */
    public double x() {
        return position.x();
    }

    /** The candidate's y coordinate, for the Y-band clamp. */
    public double y() {
        return position.y();
    }

    /** The candidate's z coordinate, for the radius/border check. */
    public double z() {
        return position.z();
    }

    /** The material the player would land on, when the adapter read it. */
    public Optional<BlockTypeName> landing() {
        return Optional.ofNullable(landingBlock);
    }
}
