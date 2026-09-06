package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * A resolved player-warp quota: either a concrete cap or "unlimited". The application layer resolves the raw
 * number through the shared {@code Permissions} quota reducer (the highest matching
 * {@code uxmessentials.pwarp.limit.<n>} node, optionally world-scoped, falling back to the config default)
 * and hands the aggregate this value object, so the {@code -1} unlimited sentinel never leaks into the count
 * comparison as a magic number.
 *
 * @param cap the maximum number of player-warps, or any value when {@link #unlimited}
 * @param unlimited true when the owner may keep any number of player-warps
 */
public record PlayerWarpLimit(int cap, boolean unlimited) {

    /** A concrete cap; a negative value is rejected (the unlimited case has its own factory). */
    public static PlayerWarpLimit of(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("player warp limit must not be negative: " + cap);
        }
        return new PlayerWarpLimit(cap, false);
    }

    /** The "no limit at all" quota: a player with the {@code -1} sentinel or unlimited meta. */
    public static PlayerWarpLimit noLimit() {
        return new PlayerWarpLimit(Integer.MAX_VALUE, true);
    }

    /** True when one more player-warp would exceed the cap (always false when unlimited). */
    public boolean isReachedAt(int currentCount) {
        return !unlimited && currentCount >= cap;
    }
}
