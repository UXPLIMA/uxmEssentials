package com.uxplima.uxmessentials.ranks.domain;

/**
 * A player's prestige level: how many times they have reset the ladder from the top back to the first rank. It
 * starts at {@link #INITIAL zero} for a player who has never prestiged and only ever increases. The level is
 * persisted in {@code player_ranks.prestige}, and the pure prestige rules live here as small, side-effect-free
 * methods the prestige use case leans on: the one legal transition {@link #increment()}, the level-cap check
 * {@link #belowCap(int)}, and the {@link #rewardMultiplier(double)} a player has earned at this level.
 *
 * @param level the prestige count, never negative
 */
public record Prestige(int level) {

    /** The prestige of a player who has never prestiged. */
    public static final Prestige INITIAL = new Prestige(0);

    public Prestige {
        if (level < 0) {
            throw new IllegalArgumentException("prestige level must not be negative: " + level);
        }
    }

    /** The next prestige level up. */
    public Prestige increment() {
        return new Prestige(level + 1);
    }

    /** Whether the player has never prestiged. */
    public boolean isInitial() {
        return level == 0;
    }

    /**
     * Whether a player at this level may still prestige, given the operator's {@code maxLevel} cap. A cap of
     * {@code 0} or below means unlimited, so the answer is always {@code true}; otherwise the player may prestige
     * only while their current level is strictly below the cap ({@code maxLevel} is the highest level reachable
     * at {@code level == maxLevel} there is nowhere left to go).
     */
    public boolean belowCap(int maxLevel) {
        return maxLevel <= 0 || level < maxLevel;
    }

    /**
     * The effective reward multiplier a player has earned at this prestige level, given the operator's configured
     * per-level {@code multiplier}. The rule is linear so each prestige adds the same bonus: the effective
     * multiplier is {@code 1 + level * (multiplier - 1)}. So a configured {@code 1.0} always yields {@code 1.0}
     * (prestige grants no reward bonus), a configured {@code 1.5} yields {@code 1.5} at level 1, {@code 2.0} at
     * level 2, and so on; at level zero the multiplier is always {@code 1.0}.
     */
    public double rewardMultiplier(double multiplier) {
        return 1.0 + level * (multiplier - 1.0);
    }
}
