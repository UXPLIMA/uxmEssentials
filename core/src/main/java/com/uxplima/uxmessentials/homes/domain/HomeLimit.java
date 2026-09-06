package com.uxplima.uxmessentials.homes.domain;

/**
 * A resolved home quota: either a concrete cap or "unlimited". The application layer resolves the raw
 * number through the shared {@code Permissions} quota reducer (the highest matching
 * {@code uxmessentials.home.limit.<n>} node, optionally world-scoped, falling back to the config
 * default) and hands the aggregate this value object, so the {@code -1} unlimited sentinel never leaks
 * into the count comparison as a magic number.
 *
 * @param cap the maximum number of homes, or any value when {@link #unlimited}
 * @param unlimited true when the owner may keep any number of homes
 */
public record HomeLimit(int cap, boolean unlimited) {

    /** A concrete cap; a negative value is rejected (the unlimited case has its own factory). */
    public static HomeLimit of(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("home limit must not be negative: " + cap);
        }
        return new HomeLimit(cap, false);
    }

    /** The "no limit at all" quota: a player with the {@code -1} sentinel or unlimited meta. */
    public static HomeLimit noLimit() {
        return new HomeLimit(Integer.MAX_VALUE, true);
    }

    /** True when one more home would exceed the cap (always false when unlimited). */
    public boolean isReachedAt(int currentCount) {
        return !unlimited && currentCount >= cap;
    }

    /**
     * The number of slots the owner may address. A concrete cap is its own slot ceiling; an unlimited
     * quota has no natural ceiling, so the caller supplies {@code unlimitedCap}: the configured upper
     * bound on the slot grid: to keep the {@code -1} sentinel from becoming an unbounded index.
     */
    public int maxSlots(int unlimitedCap) {
        return unlimited ? unlimitedCap : cap;
    }
}
