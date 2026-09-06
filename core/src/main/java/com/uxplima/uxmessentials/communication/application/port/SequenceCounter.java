package com.uxplima.uxmessentials.communication.application.port;

/**
 * Outbound port over the monotonically-advancing rotation index a {@link com.uxplima.uxmessentials.communication.domain.Ordering#SEQUENTIAL}
 * connection channel reads. Each named channel ({@code join}, {@code quit}, {@code death}) keeps its own counter,
 * so successive joins walk the join templates in author order while quits walk theirs independently. The
 * {@code MessagePolicy} wraps the returned index modulo its template count, so the counter never needs to know
 * how many templates a channel has.
 *
 * <p>The adapter backs this with an in-memory {@code AtomicLong} per channel. Transient state dropped on module
 * stop, never persisted. {@link #next} returns the current value and advances atomically, so two joins arriving
 * on different region threads never read the same index.
 */
public interface SequenceCounter {

    /**
     * The next rotation index for {@code channel}, advancing the counter atomically. The value is non-negative
     * and grows monotonically; the caller reduces it modulo the channel's template count.
     */
    int next(String channel);
}
