package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.ChunkAccess;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.BlockTypeName;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;
import com.uxplima.uxmessentials.teleport.domain.YBand;
import org.junit.jupiter.api.Test;

/**
 * Pins the async safe-search primitive over a fake {@link ChunkAccess}: the finder samples a point, asks the
 * port to probe it (never a synchronous chunk read here. The port is a plain future), and runs the pure
 * {@link SafeSearchPolicy} over whatever the probe returns. A safe candidate becomes an {@link
 * RtpSafeLocation}; an unsafe one, an excluded-biome one, and an absent probe all resolve to empty, and the
 * absent-probe case must complete without blocking, proving the finder never orphans a queue slot.
 */
class AsyncSafeLocationFinderTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    // A wide annulus centred on the origin so any candidate the fake places well inside it is in bounds; the
    // border is effectively unbounded, so only the biome / ground checks decide the verdict.
    private final SafeSearchArea area = new SafeSearchArea(WORLD, 0.0, 0.0, 0.0, 10_000.0, 1_000_000.0);

    // The real, pure policy: reused so the test proves the finder actually consults it, not a stub.
    private final SafeSearchPolicy policy = new SafeSearchPolicy(
            Set.of(BiomeName.of("ocean")), Set.of(BlockTypeName.of("lava")), YBand.unbounded(), false);

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void aSafeCandidateBecomesAnRtpSafeLocation() {
        SafeCandidate safe = new SafeCandidate(
                Position.of(WORLD, 500.5, 71.0, 500.5),
                BiomeName.of("plains"),
                true,
                false,
                BlockTypeName.of("grass_block"));
        AsyncSafeLocationFinder finder = new AsyncSafeLocationFinder(probeReturning(Optional.of(safe)), policy, clock);

        Optional<RtpSafeLocation> found = finder.find(area).join();

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().position()).isEqualTo(safe.position());
        assertThat(found.orElseThrow().validatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void aCandidateWithUnsafeGroundYieldsNoLocation() {
        // Standing-safe is false (no headroom / liquid column): the policy rejects on UNSAFE_GROUND.
        SafeCandidate unsafe = new SafeCandidate(
                Position.of(WORLD, 500.5, 63.0, 500.5),
                BiomeName.of("plains"),
                false,
                false,
                BlockTypeName.of("water"));
        AsyncSafeLocationFinder finder =
                new AsyncSafeLocationFinder(probeReturning(Optional.of(unsafe)), policy, clock);

        assertThat(finder.find(area).join()).isEmpty();
    }

    @Test
    void anExcludedBiomeCandidateIsRejectedProvingThePolicyIsConsulted() {
        // Standing-safe is true, so only the excluded-biome rule can reject it. That it does proves the
        // finder runs the candidate through the SafeSearchPolicy rather than accepting any probed column.
        SafeCandidate ocean = new SafeCandidate(
                Position.of(WORLD, 200.5, 80.0, 200.5), BiomeName.of("ocean"), true, false, BlockTypeName.of("sand"));
        AsyncSafeLocationFinder finder = new AsyncSafeLocationFinder(probeReturning(Optional.of(ocean)), policy, clock);

        assertThat(finder.find(area).join()).isEmpty();
    }

    @Test
    void anAbsentProbeYieldsAnAlreadyCompletedEmptyResult() {
        // A probe that resolves empty (absent world / failed load) must not hang the finder: the returned
        // future is already done and empty, never orphaned, so the refill loop reclaims the slot at once.
        AsyncSafeLocationFinder finder = new AsyncSafeLocationFinder(probeReturning(Optional.empty()), policy, clock);

        CompletableFuture<Optional<RtpSafeLocation>> result = finder.find(area);

        assertThat(result).isCompleted();
        assertThat(result.join()).isEmpty();
    }

    private static ChunkAccess probeReturning(Optional<SafeCandidate> candidate) {
        return (probeArea, blockX, blockZ) -> CompletableFuture.completedFuture(candidate);
    }
}
