package com.uxplima.uxmessentials.discord;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * A small sliding-window throttle that protects the Discord channel (and JDA's REST budget) from a flood of
 * host events, a mass {@code /kickall}, a bulk eco-admin sweep, a migration import emitting thousands of
 * per-record audit lines (CLAUDE.md: "rate-limit/batch if a flood of events"). It admits up to
 * {@code maxPerWindow} notifications per rolling {@code window}; once the window's budget is spent, further
 * notifications are dropped until the window rolls over, so the bridge degrades by shedding load rather than
 * hammering Discord into a 429.
 *
 * <p>The clock is injected as a {@link LongSupplier} of epoch millis so the throttle is tested deterministically
 * without sleeping. State is two longs guarded by atomics; {@link #tryAcquire()} is cheap enough for the
 * notification hot path and safe to call from the host's emitting thread.
 */
public final class NotificationRateLimiter {

    private final int maxPerWindow;
    private final long windowMillis;
    private final LongSupplier clock;

    private final AtomicLong windowStart;
    private final AtomicLong count = new AtomicLong();

    public NotificationRateLimiter(int maxPerWindow, Duration window, LongSupplier clock) {
        if (maxPerWindow <= 0) {
            throw new IllegalArgumentException("maxPerWindow must be > 0");
        }
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = Objects.requireNonNull(window, "window").toMillis();
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.windowStart = new AtomicLong(clock.getAsLong());
    }

    /**
     * Try to admit one notification. Returns {@code true} when there is budget left in the current window (and
     * consumes it), {@code false} when the window's budget is exhausted and the notification should be dropped.
     * Rolls the window forward when the configured duration has elapsed.
     */
    public synchronized boolean tryAcquire() {
        long now = clock.getAsLong();
        if (now - windowStart.get() >= windowMillis) {
            windowStart.set(now);
            count.set(0L);
        }
        if (count.get() >= maxPerWindow) {
            return false;
        }
        count.incrementAndGet();
        return true;
    }
}
