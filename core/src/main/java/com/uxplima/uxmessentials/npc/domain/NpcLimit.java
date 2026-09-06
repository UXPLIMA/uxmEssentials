package com.uxplima.uxmessentials.npc.domain;

/**
 * A resolved per-owner NPC quota: either a concrete cap or "unlimited". The application layer resolves the raw
 * number through the shared {@code Permissions} quota reducer (the highest matching
 * {@code uxmessentials.npc.limit.<n>} node, falling back to the config default) and hands this value object to
 * the create path, so the {@code -1} unlimited sentinel never leaks into the count comparison as a magic number.
 * Mirrors {@code homes.domain.HomeLimit}; an NPC quota is server-wide (not world-scoped), so there is no slot grid.
 *
 * @param cap the maximum number of NPCs the owner may have, or any value when {@link #unlimited}
 * @param unlimited true when the owner may create any number of NPCs
 */
public record NpcLimit(int cap, boolean unlimited) {

    /** A concrete cap; a negative value is rejected (the unlimited case has its own factory). */
    public static NpcLimit of(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("npc limit must not be negative: " + cap);
        }
        return new NpcLimit(cap, false);
    }

    /** The "no limit at all" quota: a player with the {@code -1} sentinel, unlimited meta, or unlimited default. */
    public static NpcLimit noLimit() {
        return new NpcLimit(Integer.MAX_VALUE, true);
    }

    /** True when one more NPC would exceed the cap (always false when unlimited). */
    public boolean isReachedAt(int currentCount) {
        return !unlimited && currentCount >= cap;
    }
}
