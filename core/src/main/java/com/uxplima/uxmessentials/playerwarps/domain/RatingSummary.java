package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * The denormalised rollup of a warp's star ratings, kept on the aggregate so a browse menu can sort and render
 * thousands of warps without touching the per-vote rating table. It carries the raw totals ({@link #sum} of stars
 * over {@link #count} votes) alongside two derived numbers: the plain {@link #average} and a {@link #score}, the
 * ranking value the "top rated" listing sorts on.
 *
 * <p>The {@link #score} is a Bayesian-adjusted figure (so a single five-star vote does not outrank a warp with
 * hundreds of votes averaging 4.8) that is computed elsewhere, in P6, where the smoothing constants live in
 * config. This value object only stores whatever score it is handed; deliberately not recomputing it here keeps
 * the ranking policy in one place instead of leaking config into the domain type.
 *
 * @param sum total stars awarded across all votes
 * @param count number of votes cast
 * @param average mean stars per vote
 * @param score the ranking score the listings sort on
 */
public record RatingSummary(long sum, int count, double average, double score) {

    public RatingSummary {
        if (sum < 0) {
            throw new IllegalArgumentException("rating sum must not be negative: " + sum);
        }
        if (count < 0) {
            throw new IllegalArgumentException("rating count must not be negative: " + count);
        }
        if (!Double.isFinite(average)) {
            throw new IllegalArgumentException("rating average must be finite: " + average);
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("rating score must be finite: " + score);
        }
    }

    /** The rollup for a warp nobody has rated yet. */
    public static RatingSummary empty() {
        return new RatingSummary(0L, 0, 0.0, 0.0);
    }

    /** Build a rollup from already-computed totals and scores. */
    public static RatingSummary of(long sum, int count, double average, double score) {
        return new RatingSummary(sum, count, average, score);
    }
}
