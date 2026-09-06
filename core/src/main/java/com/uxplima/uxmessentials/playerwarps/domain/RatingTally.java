package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * The raw totals of a warp's star ratings, straight from the rating table: the {@link #sum} of every awarded star
 * over the {@link #count} of votes cast. It is the un-smoothed input the ranking policy turns into a
 * {@link RatingSummary}. The plain average is {@code sum / count}, and the Bayesian score folds in the global mean
 * ({@link BayesianRating}). A warp nobody has rated is {@code (0, 0)}; guarding the division by a zero count is the
 * policy's concern, not this value object's, so the tally itself never divides.
 *
 * @param sum total stars awarded across all votes (never negative)
 * @param count number of votes cast (never negative)
 */
public record RatingTally(long sum, int count) {

    public RatingTally {
        if (sum < 0) {
            throw new IllegalArgumentException("rating sum must not be negative: " + sum);
        }
        if (count < 0) {
            throw new IllegalArgumentException("rating count must not be negative: " + count);
        }
    }

    /** The tally for a warp nobody has rated yet. */
    public static RatingTally empty() {
        return new RatingTally(0L, 0);
    }
}
