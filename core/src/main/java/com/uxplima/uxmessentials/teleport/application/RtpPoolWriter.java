package com.uxplima.uxmessentials.teleport.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;

/**
 * The persist-on-validate half of the durable RTP pool: it accumulates validated columns per world and, when a
 * refill cycle flushes, writes the batch to the {@link RtpPoolStore} <strong>off the tick thread</strong> through
 * the {@link Scheduler} port: never one synchronous row-insert per candidate. Batching per refill cycle keeps the
 * single-writer SQLite backend from being hammered, and dispatching the save async keeps the relational I/O off any
 * region thread.
 *
 * <p>Columns are de-duplicated within a world's buffer by their block {@code (x, z)}: two validations of the same
 * column in one cycle collapse to the freshest, so a batch never carries a redundant row. A later {@code flush}
 * drains only the buffered set, so a column recorded after the drain rolls into the next cycle rather than being
 * lost or double-written.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code buffers} holds one {@link ConcurrentHashMap} of packed-{@code
 * (x,z)} → column per world; {@link #record} and {@link #flush} run on the search chain's async threads, and the
 * per-world buffer is atomically {@link Map#remove removed} on flush so a concurrent record either lands in the
 * drained batch or the next one, never in a torn read.
 */
public final class RtpPoolWriter implements RtpPoolSink {

    private final RtpPoolStore store;
    private final Scheduler scheduler;
    private final Logger log;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, RtpColumn>> buffers = new ConcurrentHashMap<>();

    public RtpPoolWriter(RtpPoolStore store, Scheduler scheduler, Logger log) {
        this.store = Objects.requireNonNull(store, "store");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void record(RtpColumn column) {
        Objects.requireNonNull(column, "column");
        buffers.computeIfAbsent(column.world().uid(), id -> new ConcurrentHashMap<>())
                .put(packed(column), column);
    }

    @Override
    public void flush(WorldRef world) {
        Objects.requireNonNull(world, "world");
        Map<Long, RtpColumn> drained = buffers.remove(world.uid());
        if (drained == null || drained.isEmpty()) {
            return;
        }
        List<RtpColumn> batch = List.copyOf(drained.values());
        scheduler.async(() -> save(world, batch));
    }

    private void save(WorldRef world, Collection<RtpColumn> batch) {
        try {
            store.save(world, batch);
        } catch (RuntimeException failure) {
            log.warn("rtp pool persist failed for world {}: {}", world.name(), String.valueOf(failure.getMessage()));
        }
    }

    /** Pack a column's block {@code (x, z)} into one long key so a world's buffer dedupes by coordinate. */
    private static long packed(RtpColumn column) {
        return (((long) column.x()) << 32) ^ (column.z() & 0xFFFF_FFFFL);
    }
}
