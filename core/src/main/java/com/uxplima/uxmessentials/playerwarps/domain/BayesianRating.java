package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;

/**
 * The ranking policy that turns a warp's raw {@link RatingTally} into the {@link RatingSummary} the "top rated"
 * browse sorts on. It is a Bayesian weighted rating: every warp's score is pulled toward the global mean {@code m}
 * by a notional {@code confidence} votes' worth of prior, so a lone five-star vote cannot outrank a warp with
 * hundreds of votes averaging a shade under five. With {@code C} the confidence constant and {@code m} the mean
 * star across every rating on the server,
 *
 * <pre>score = (C * m + sum) / (C + count)</pre>
 *
 * As a warp's vote count grows the prior's weight fades and the score converges on the warp's own average; with few
 * votes it sits close to {@code m}. A warp with no votes scores zero rather than {@code m}, so an unrated warp never
 * floats above a genuinely-rated one. The confidence is a config value, injected here so the policy stays pure and
 * unit-testable; {@link RatingSummary} deliberately does not recompute: it only stores what this hands it.
 *
 * @param confidence the smoothing constant {@code C}: how many global-mean votes of prior every warp starts with
 */
public record BayesianRating(int confidence) {

    public BayesianRating {
        if (confidence < 0) {
            throw new IllegalArgumentException("rating confidence must not be negative: " + confidence);
        }
    }

    /**
     * Roll {@code tally} up into a {@link RatingSummary}, folding {@code globalMean} in through the Bayesian prior.
     * An empty tally (no votes) yields {@link RatingSummary#empty()}. Both the average and the score are zero, so
     * an unrated warp sorts below every rated one.
     *
     * @param tally the warp's raw star sum and vote count
     * @param globalMean the mean star across every rating on the server ({@code 0.0} when there are none)
     */
    public RatingSummary summarise(RatingTally tally, double globalMean) {
        Objects.requireNonNull(tally, "tally");
        if (tally.count() == 0) {
            return RatingSummary.empty();
        }
        double average = (double) tally.sum() / tally.count();
        double score = (confidence * globalMean + tally.sum()) / (confidence + tally.count());
        return RatingSummary.of(tally.sum(), tally.count(), average, score);
    }
}
