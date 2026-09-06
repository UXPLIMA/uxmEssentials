package com.uxplima.uxmessentials.trade.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * The per-player cooldown between {@code /trade} requests, an in-memory stamp per requester, driven by the injected
 * {@link Clock}. {@link #remainingSeconds} returns how many whole seconds a player must still wait (rounded up, so a
 * partial second still reads as "wait 1s"); {@code 0} means they may send now, which is always the case when the
 * configured window is zero. The decision is pure and unit-testable with a controllable clock.
 */
@NullMarked
public final class TradeCooldown {

    private final Clock clock;
    private final Duration window;
    private final ConcurrentHashMap<UUID, Instant> lastRequest = new ConcurrentHashMap<>();

    public TradeCooldown(Clock clock, Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.window = Objects.requireNonNull(window, "window");
        if (window.isNegative()) {
            throw new IllegalArgumentException("cooldown window must not be negative: " + window);
        }
    }

    /** Whole seconds {@code player} must still wait before sending another request; {@code 0} when they may send now. */
    public long remainingSeconds(UUID player) {
        Objects.requireNonNull(player, "player");
        if (window.isZero()) {
            return 0;
        }
        Instant last = lastRequest.get(player);
        if (last == null) {
            return 0;
        }
        long remainingMillis =
                window.toMillis() - Duration.between(last, clock.instant()).toMillis();
        return remainingMillis <= 0 ? 0 : (remainingMillis + 999) / 1000;
    }

    /** Record that {@code player} has just sent a request, starting their cooldown. */
    public void stamp(UUID player) {
        Objects.requireNonNull(player, "player");
        lastRequest.put(player, clock.instant());
    }

    /** Forget a player's stamp on disconnect so the map does not grow without bound. */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        lastRequest.remove(player);
    }
}
