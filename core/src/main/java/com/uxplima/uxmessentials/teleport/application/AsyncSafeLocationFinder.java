package com.uxplima.uxmessentials.teleport.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots;
import com.uxplima.uxmessentials.teleport.application.port.ChunkAccess;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;

/**
 * The random-teleport safe-search primitive, rebuilt around an asynchronous {@link ChunkAccess} port. One
 * call to {@link #find} samples a random point in a world's {@link SafeSearchArea}, asks the port to probe
 * that column (the port loads the chunk off-thread, copies a snapshot, and releases it: no synchronous
 * chunk load ever happens on a tick thread), and runs the pure {@link SafeSearchPolicy} over whatever facts
 * the probe returns. A candidate that passes becomes an {@link RtpSafeLocation}; anything else, an unsafe
 * column, an excluded biome, or an absent probe, resolves to {@link Optional#empty()}.
 *
 * <p>This replaces the old {@code SafeSearchValidator}, whose probe read the candidate's chunk synchronously
 * inside a region-thread task and so generated a far chunk on the main thread on every attempt. Here the
 * finder itself is pure and never blocks: it composes onto the port's future with {@code thenApply}, so the
 * verdict runs on whatever thread the port completes on and the caller gets a plain future back. Because the
 * port always completes (empty on any failure), the finder's future is never orphaned either.
 *
 * <p>Sampling is delegated to a {@link HotspotBiasedSampler}: an ordinary search draws uniformly over the annulus
 * ({@code r = sqrt(rand·(max² − min²) + min²)} with an independent uniform angle, so points do not clump toward the
 * centre), while a biome-targeted {@link SafeSearchArea} biases the draw toward a learned rare-biome hotspot. Both
 * paths run through this one finder; an untargeted area samples exactly as the previous engine did.
 */
public final class AsyncSafeLocationFinder {

    private final ChunkAccess chunkAccess;
    private final SafeSearchPolicy policy;
    private final Clock clock;
    private final HotspotBiasedSampler sampler;

    /** An untargeted finder with plain uniform sampling, no rare-biome hotspot bias. */
    public AsyncSafeLocationFinder(ChunkAccess chunkAccess, SafeSearchPolicy policy, Clock clock) {
        this(chunkAccess, policy, clock, new HotspotBiasedSampler(BiomeHotspots.NONE, 0.0, 1));
    }

    public AsyncSafeLocationFinder(
            ChunkAccess chunkAccess, SafeSearchPolicy policy, Clock clock, HotspotBiasedSampler sampler) {
        this.chunkAccess = Objects.requireNonNull(chunkAccess, "chunkAccess");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    /**
     * Sample one random candidate in {@code area}, probe it asynchronously, and judge it with the pure
     * policy. The future completes with the accepted {@link RtpSafeLocation} or empty: never blocking,
     * never orphaned.
     */
    public CompletableFuture<Optional<RtpSafeLocation>> find(SafeSearchArea area) {
        Objects.requireNonNull(area, "area");
        double[] xz = sampler.sample(area, ThreadLocalRandom.current());
        int blockX = (int) Math.floor(xz[0]);
        int blockZ = (int) Math.floor(xz[1]);
        return probeColumn(area, blockX, blockZ);
    }

    /**
     * Re-probe a <em>specific</em> column {@code (blockX, blockZ)} rather than a random sample, running the
     * exact same async probe and pure-policy verdict as {@link #find}. This is the startup pre-warm's primitive:
     * a persisted {@link RtpSafeLocation}'s column is trusted only as a horizontal coordinate, so it is re-probed
     * here. A fresh landing Y is resolved and the full {@link SafeSearchPolicy} is re-run off the async chunk read
     *: before it re-enters the servable queue. A known-good column almost always still passes (one cheap probe),
     * but a column the world changed under (a rollback, a regenerated chunk, a shrunk border) resolves to {@link
     * Optional#empty()} and is dropped, never served stale.
     */
    public CompletableFuture<Optional<RtpSafeLocation>> probeColumn(SafeSearchArea area, int blockX, int blockZ) {
        Objects.requireNonNull(area, "area");
        return chunkAccess.probe(area, blockX, blockZ).thenApply(candidate -> judge(area, candidate));
    }

    private Optional<RtpSafeLocation> judge(SafeSearchArea area, Optional<SafeCandidate> candidate) {
        return candidate.flatMap(c -> policy.accept(area, c, clock.instant()).asValue());
    }
}
