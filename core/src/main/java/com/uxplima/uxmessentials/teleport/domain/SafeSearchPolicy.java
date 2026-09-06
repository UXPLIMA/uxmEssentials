package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The pure decision logic of the random-teleport safe-location search: given a world's {@link
 * SafeSearchArea}, its excluded biomes, avoided landing blocks, the permitted Y band, and the
 * avoid-protected-land flag, decide whether one {@link SafeCandidate} the adapter validated off-thread is
 * acceptable. This is the queue's <em>refill primitive</em>'s verdict step, the random-point generation,
 * async chunk load, biome read, material read, safe-Y resolution, and the claim/region protection read all
 * happen in the adapter; the policy only judges the resulting facts.
 *
 * <p>The policy is deliberately side-effect-free and clock-injected ({@link #accept} takes the
 * validation {@code Instant}) so it is trivially unit-testable and never touches Bukkit. The ordering
 * of the checks is cheapest-first: a coordinate out of bounds is rejected before the biome set is even
 * consulted.
 *
 * @param excludedBiomes biomes a candidate may not land in (lower-cased {@link BiomeName})
 * @param avoidBlocks materials a candidate may not land on (lower-cased {@link BlockTypeName})
 * @param yBand the vertical band a candidate's landing Y must fall within
 * @param avoidProtected whether candidates on protected land, inside a claim or a WorldGuard region, are
 *     rejected; the adapter folds the {@code respect-claims} / {@code respect-worldguard} toggles into the
 *     candidate's {@link SafeCandidate#insideClaim} flag, and this gate decides whether that flag vetoes
 */
public record SafeSearchPolicy(
        Set<BiomeName> excludedBiomes, Set<BlockTypeName> avoidBlocks, YBand yBand, boolean avoidProtected) {

    public SafeSearchPolicy {
        Objects.requireNonNull(excludedBiomes, "excludedBiomes");
        Objects.requireNonNull(avoidBlocks, "avoidBlocks");
        Objects.requireNonNull(yBand, "yBand");
        excludedBiomes = Set.copyOf(excludedBiomes);
        avoidBlocks = Set.copyOf(avoidBlocks);
    }

    /** A policy with only excluded biomes and protected-land avoidance: no avoid-blocks, no Y clamp. */
    public SafeSearchPolicy(Set<BiomeName> excludedBiomes, boolean avoidProtected) {
        this(excludedBiomes, Set.of(), YBand.unbounded(), avoidProtected);
    }

    /** A policy that excludes no biomes, avoids no blocks, clamps no Y, and ignores claims. */
    public static SafeSearchPolicy permissive() {
        return new SafeSearchPolicy(Set.of(), Set.of(), YBand.unbounded(), false);
    }

    /**
     * Judge a validated candidate against {@code area}. On success the candidate becomes an {@link
     * RtpSafeLocation} stamped with {@code validatedAt}; on failure the {@link SafeRejection} explains
     * why, for refill diagnostics.
     */
    public Result<RtpSafeLocation, SafeRejection> accept(
            SafeSearchArea area, SafeCandidate candidate, Instant validatedAt) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(validatedAt, "validatedAt");
        Optional<SafeRejection> rejection = firstFailure(area, candidate);
        if (rejection.isPresent()) {
            return Result.err(rejection.get());
        }
        double radius = area.radiusOf(candidate.x(), candidate.z());
        return Result.ok(new RtpSafeLocation(candidate.position(), radius, candidate.biome(), validatedAt));
    }

    private Optional<SafeRejection> firstFailure(SafeSearchArea area, SafeCandidate candidate) {
        if (!area.contains(candidate.x(), candidate.z())) {
            return Optional.of(SafeRejection.OUT_OF_BOUNDS);
        }
        if (!yBand.contains(candidate.y())) {
            return Optional.of(SafeRejection.OUT_OF_Y_BAND);
        }
        if (excludedBiomes.contains(candidate.biome())) {
            return Optional.of(SafeRejection.EXCLUDED_BIOME);
        }
        if (biomeMismatch(area, candidate)) {
            return Optional.of(SafeRejection.BIOME_MISMATCH);
        }
        if (!candidate.standingSafe()) {
            return Optional.of(SafeRejection.UNSAFE_GROUND);
        }
        if (landsOnAvoidedBlock(candidate)) {
            return Optional.of(SafeRejection.AVOIDED_BLOCK);
        }
        if (avoidProtected && candidate.insideClaim()) {
            return Optional.of(SafeRejection.INSIDE_CLAIM);
        }
        return Optional.empty();
    }

    private boolean landsOnAvoidedBlock(SafeCandidate candidate) {
        return candidate.landing().map(avoidBlocks::contains).orElse(false);
    }

    /** True when the area targets a biome the candidate did not validate in, the {@code /rtp biome} gate. */
    private static boolean biomeMismatch(SafeSearchArea area, SafeCandidate candidate) {
        return area.targetBiomeName()
                .map(target -> !target.equals(candidate.biome()))
                .orElse(false);
    }

    /** True when {@code biome} is excluded, exposed for the cheap on-serve revalidation. */
    public boolean excludes(BiomeName biome) {
        return excludedBiomes.contains(Objects.requireNonNull(biome, "biome"));
    }
}
