package com.uxplima.uxmessentials.teleport.application.port;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;

/**
 * Outbound port over the durable random-teleport pool (ADR 0010, the {@code rtp_pool} table). It is the
 * restart-surviving mirror of the in-memory {@code SafeLocationQueue}: the refill persists each validated
 * column here, and on enable the module loads a bounded set per world and re-probes them to warm the queue
 * before the first {@code /rtp}. Serving itself never touches this store. The queue is polled O(1); the
 * store only keeps the queue warm across restarts.
 *
 * <p>Every method is a synchronous relational operation, so a caller <strong>must</strong> invoke it off a
 * tick thread (through the {@code Scheduler} port's async dispatch). The SQLite default is single-writer and
 * a query blocks. The implementation bounds each world's pool to a configured cap on {@link #save} so the
 * table cannot grow without limit, and {@link #deleteStale} sweeps entries whose validation has aged out.
 */
public interface RtpPoolStore {

    /**
     * Persist a batch of freshly validated {@code columns} for {@code world}, then evict the oldest rows
     * beyond the per-world cap so the stored pool stays bounded. An empty batch is a no-op. Called off-tick,
     * batched per refill cycle, never one row per candidate synchronously.
     */
    void save(WorldRef world, Collection<RtpColumn> columns);

    /**
     * The freshest up-to {@code limit} persisted columns for {@code world}, newest validation first, for the
     * startup pre-warm to re-probe. Never blocks the tick thread: the caller dispatches it async.
     */
    List<RtpColumn> load(WorldRef world, int limit);

    /**
     * The freshest up-to {@code limit} persisted columns for {@code world} that validated in {@code biome}, newest
     * validation first, the per-biome pool slice a {@code /rtp biome <biome>} serves from before it falls back to a
     * live biome-targeted search. Only columns whose stored biome equals {@code biome} are returned (a column with no
     * recorded biome never matches). Never blocks the tick thread: the caller dispatches it async.
     */
    List<RtpColumn> loadByBiome(WorldRef world, BiomeName biome, int limit);

    /**
     * Remove every column across all worlds whose validation is older than {@code olderThan}, returning how
     * many rows were deleted. Run once on enable so a long-dead column is not re-probed forever.
     */
    int deleteStale(Duration olderThan);

    /** How many columns are currently stored for {@code world}. */
    int count(WorldRef world);
}
