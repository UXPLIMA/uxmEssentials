package com.uxplima.uxmessentials.survival.domain;

/**
 * The pure one-player-sleep threshold: given how many eligible players are sleeping in a world and how many are
 * eligible in all, decide whether enough are asleep to skip the night. It is expressed two ways and one of them wins:
 *
 * <ul>
 *   <li><b>a fixed count</b> ({@code requiredCount}). Met when at least that many players are sleeping, taken
 *       verbatim, so the eponymous one-player-sleep is simply {@code requiredCount = 1}; and
 *   <li><b>a percentage</b> ({@code requiredPercent}) of the eligible players, rounded up to a whole player and never
 *       below one.
 * </ul>
 *
 * <p><b>Precedence: the fixed count wins whenever it is positive.</b> {@code requiredCount > 0} uses the count and
 * ignores the percentage entirely; only {@code requiredCount == 0} falls through to the percentage. A count larger
 * than the online population is honoured literally. The night simply will not skip until that many players are
 * present and sleeping, which is the operator's explicit intent when they raise it above the default of one.
 *
 * <p>Pure: it works on two plain integers and the two configured thresholds, so it is unit-testable with no Bukkit in
 * sight. The adapter counts the live sleepers and eligible players per world and feeds them in.
 */
public record SleepThreshold(int requiredCount, int requiredPercent) {

    public SleepThreshold {
        requiredCount = Math.max(0, requiredCount);
        requiredPercent = Math.max(0, Math.min(100, requiredPercent));
    }

    /**
     * Whether {@code sleepers} of {@code eligible} players is enough to skip the night.
     *
     * @param sleepers how many eligible players are currently sleeping; must not be negative
     * @param eligible how many players count toward the threshold at all (online, not spectating, not sleep-ignored);
     *     must not be negative
     * @return {@code true} when the sleeping players meet the configured count or percentage
     */
    public boolean isMet(int sleepers, int eligible) {
        if (sleepers < 0) {
            throw new IllegalArgumentException("sleepers must not be negative: " + sleepers);
        }
        if (eligible < 0) {
            throw new IllegalArgumentException("eligible must not be negative: " + eligible);
        }
        if (eligible == 0 || sleepers == 0) {
            return false;
        }
        return sleepers >= requiredSleepers(eligible);
    }

    /** The number of sleeping players needed to skip the night given {@code eligible} eligible players (at least one). */
    public int requiredSleepers(int eligible) {
        if (eligible <= 0) {
            throw new IllegalArgumentException("eligible must be positive: " + eligible);
        }
        if (requiredCount > 0) {
            return requiredCount;
        }
        int needed = (int) Math.ceil((double) eligible * requiredPercent / 100.0);
        return Math.max(1, needed);
    }
}
