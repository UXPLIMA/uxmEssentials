package com.uxplima.uxmessentials.ranks.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.ranks.domain.Prestige;

/**
 * The typed outcome of a {@link Prestige} attempt: which of the five terminal states it reached and, on a
 * successful prestige, the new {@link Prestige} level and the reward multiplier the player now holds. Modelling
 * the outcome as a value the command boundary renders, rather than sending a message from the use case, keeps
 * the decision logic pure and free of any {@code Messages} dependency, exactly as {@link RankupResult} does.
 *
 * <p>The {@code newPrestige} is present exactly when the status is {@link Status#PRESTIGED}; the
 * {@code rewardMultiplier} is only meaningful in that case (it is {@code 1.0} otherwise) and is surfaced to the
 * player alongside the new level.
 *
 * @param status the terminal state the attempt reached
 * @param newPrestige the level advanced to, present only when {@link Status#PRESTIGED}
 * @param rewardMultiplier the effective reward multiplier at the new level (meaningful only when prestiged)
 */
public record PrestigeResult(Status status, Optional<Prestige> newPrestige, double rewardMultiplier) {

    /** The terminal states a prestige attempt can reach, each mapping to one {@link RanksMessageKey}. */
    public enum Status {

        /** The player prestiged: the pointer reset to the first rank and {@link #newPrestige()} carries the level. */
        PRESTIGED,

        /** The player is not at the top rank yet: there is a higher rank to reach before prestiging. */
        NOT_AT_TOP,

        /** The player has reached the configured prestige cap; nothing was charged and the level did not move. */
        MAX_LEVEL,

        /** The prestige requirements were not met; nothing was charged and the level did not move. */
        REQUIREMENTS_NOT_MET,

        /** The prestige cost could not be charged; nothing was charged and the level did not move. */
        CANNOT_AFFORD
    }

    public PrestigeResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(newPrestige, "newPrestige");
        if ((status == Status.PRESTIGED) != newPrestige.isPresent()) {
            throw new IllegalArgumentException("newPrestige is present exactly when the status is PRESTIGED");
        }
    }

    /** The player prestiged to {@code newPrestige}, earning {@code rewardMultiplier}. */
    public static PrestigeResult prestiged(Prestige newPrestige, double rewardMultiplier) {
        return new PrestigeResult(Status.PRESTIGED, Optional.of(newPrestige), rewardMultiplier);
    }

    /** The player is not at the top rank, so there is nothing to prestige from. */
    public static PrestigeResult notAtTop() {
        return new PrestigeResult(Status.NOT_AT_TOP, Optional.empty(), 1.0);
    }

    /** The player has reached the prestige cap. */
    public static PrestigeResult maxLevel() {
        return new PrestigeResult(Status.MAX_LEVEL, Optional.empty(), 1.0);
    }

    /** A prestige requirement was not satisfied. */
    public static PrestigeResult requirementsNotMet() {
        return new PrestigeResult(Status.REQUIREMENTS_NOT_MET, Optional.empty(), 1.0);
    }

    /** The prestige cost could not be paid. */
    public static PrestigeResult cannotAfford() {
        return new PrestigeResult(Status.CANNOT_AFFORD, Optional.empty(), 1.0);
    }

    /** The new prestige level as a plain number, or {@code 0} when the attempt did not prestige. */
    public int newLevel() {
        return newPrestige.map(Prestige::level).orElse(0);
    }
}
