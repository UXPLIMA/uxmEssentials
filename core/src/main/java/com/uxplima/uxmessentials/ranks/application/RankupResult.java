package com.uxplima.uxmessentials.ranks.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.ranks.domain.Rank;

/**
 * The typed outcome of a {@link Rankup} attempt: which of the four terminal states it reached and, on a
 * successful advance, the {@link Rank} the player moved up to. Modelling the outcome as a value the command
 * boundary renders, rather than sending a message from the use case, keeps the decision logic pure and free
 * of any {@code Messages} dependency, mirroring how the poses context returns an outcome the command maps to a
 * catalog key.
 *
 * @param status the terminal state the attempt reached
 * @param newRank the rank advanced to, present only when {@link Status#RANKED_UP}
 */
public record RankupResult(Status status, Optional<Rank> newRank) {

    /** The terminal states a rankup attempt can reach, each mapping to one {@link RanksMessageKey}. */
    public enum Status {

        /** The player advanced to the next rank; {@link RankupResult#newRank()} carries it. */
        RANKED_UP,

        /** There is no next rank: the player is already at the top (or the ladder holds no ranks). */
        ALREADY_MAX,

        /** The next rank's requirements were not met; nothing was charged and the pointer did not move. */
        REQUIREMENTS_NOT_MET,

        /** The next rank's cost could not be charged; nothing was charged and the pointer did not move. */
        CANNOT_AFFORD
    }

    public RankupResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(newRank, "newRank");
        if ((status == Status.RANKED_UP) != newRank.isPresent()) {
            throw new IllegalArgumentException("newRank is present exactly when the status is RANKED_UP");
        }
    }

    /** The player advanced to {@code newRank}. */
    public static RankupResult rankedUp(Rank newRank) {
        return new RankupResult(Status.RANKED_UP, Optional.of(newRank));
    }

    /** There was no next rank to advance to. */
    public static RankupResult alreadyMax() {
        return new RankupResult(Status.ALREADY_MAX, Optional.empty());
    }

    /** A requirement of the next rank was not satisfied. */
    public static RankupResult requirementsNotMet() {
        return new RankupResult(Status.REQUIREMENTS_NOT_MET, Optional.empty());
    }

    /** The next rank's cost could not be paid. */
    public static RankupResult cannotAfford() {
        return new RankupResult(Status.CANNOT_AFFORD, Optional.empty());
    }
}
