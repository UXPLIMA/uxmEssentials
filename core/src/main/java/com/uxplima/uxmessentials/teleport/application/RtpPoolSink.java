package com.uxplima.uxmessentials.teleport.application;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;

/**
 * The seam the pre-warmed queue hands each validated column to for durable persistence. The refill records a
 * column the instant a search validates it, and flushes the world's accumulated batch once a refill cycle
 * finishes, so the persist path never writes one row per candidate synchronously on the search thread.
 *
 * <p>The real implementation is {@link RtpPoolWriter} (batch, then save off-tick). When the persisted pool is
 * switched off in config, the queue holds {@link #NONE} instead, so a disabled pool records nothing and
 * schedules no writes at all: the queue keeps working purely in memory.
 */
public interface RtpPoolSink {

    /** Buffer one freshly validated column for its world; cheap and non-blocking, no I/O here. */
    void record(RtpColumn column);

    /** Flush {@code world}'s buffered batch to durable storage off-tick; a no-op when nothing is buffered. */
    void flush(WorldRef world);

    /** A sink that discards everything: wired when the persisted pool is disabled. */
    RtpPoolSink NONE = new RtpPoolSink() {
        @Override
        public void record(RtpColumn column) {
            // The persisted pool is off: keep the queue in-memory only, storing nothing.
        }

        @Override
        public void flush(WorldRef world) {
            // Nothing was buffered, so there is nothing to flush.
        }
    };
}
