package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;

/**
 * Outbound port that resolves one random-teleport candidate column without ever loading a chunk
 * synchronously on a tick thread. The domain hands the port a block coordinate; the adapter loads the
 * destination chunk <em>asynchronously</em> (Paper's {@code World#getChunkAtAsync}), copies a
 * {@link com.uxplima.uxmessentials.teleport.domain.SafeCandidate}'s worth of facts off a chunk snapshot,
 * and releases the chunk again, so the pure {@link com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy}
 * never touches a live chunk and no probe force-generates a far chunk on the main thread.
 *
 * <p>This is the seam that fixes the RTP TPS crater: the old validator read each candidate's column with a
 * synchronous {@code getHighestBlockYAt}/{@code getBlockAt} inside a region-thread task, which generated the
 * chunk inline. The port's contract forbids that: the implementation must schedule the load off-thread.
 *
 * <p><strong>The future always completes.</strong> Every failure path, an absent world, a chunk that never
 * arrives, a read that throws, resolves to {@link Optional#empty()} rather than an orphaned or exceptional
 * future. A caller (the {@link com.uxplima.uxmessentials.teleport.application.AsyncSafeLocationFinder}) that
 * awaits the result would otherwise hang forever and burn a queue slot, so completing empty is the contract,
 * not a nicety.
 */
public interface ChunkAccess {

    /**
     * Probe the column at {@code (blockX, blockZ)} in {@code area}'s world, completing with the candidate's
     * off-thread-read facts, or {@link Optional#empty()} when the world is absent, the chunk cannot be
     * loaded, or the read fails. The returned future never completes exceptionally and is never orphaned.
     */
    CompletableFuture<Optional<SafeCandidate>> probe(SafeSearchArea area, int blockX, int blockZ);
}
