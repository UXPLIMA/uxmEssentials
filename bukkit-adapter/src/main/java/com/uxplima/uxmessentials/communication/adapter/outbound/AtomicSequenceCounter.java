package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.communication.application.port.SequenceCounter;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link SequenceCounter} implementation: one {@link AtomicLong} per named connection channel ({@code join},
 * {@code quit}, {@code death}), held in a {@code ConcurrentHashMap} created on first use. Each {@link #next} reads
 * and advances its channel's counter atomically, so two joins arriving on different region threads never read the
 * same rotation index. The value is non-negative and grows monotonically; the {@code MessagePolicy} reduces it
 * modulo the channel's template count.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The per-channel counters are transient state, created on demand and
 * dropped with the wiring on module stop: never persisted.
 */
@NullMarked
public final class AtomicSequenceCounter implements SequenceCounter {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public int next(String channel) {
        Objects.requireNonNull(channel, "channel");
        long value =
                counters.computeIfAbsent(channel, ignored -> new AtomicLong()).getAndIncrement();
        // The policy only needs a non-negative monotonic index it reduces modulo the template count; clamp the
        // long into int range so a counter that runs for years never overflows into a negative index.
        return (int) (value & Integer.MAX_VALUE);
    }
}
