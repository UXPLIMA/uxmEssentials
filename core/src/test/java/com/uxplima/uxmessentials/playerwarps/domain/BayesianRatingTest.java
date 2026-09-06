package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Bayesian ranking policy in isolation. The headline test is the defect the spec names: a single five-star vote
 * must score below a warp with hundreds of votes averaging just under five, even though its own average is higher
 * the whole reason the browse sorts on the smoothed score, not the raw average.
 */
class BayesianRatingTest {

    private static final BayesianRating SCORING = new BayesianRating(10);

    @Test
    void aLoneFiveStarVoteScoresBelowAWarpWithFiveHundredVotesAveragingNearlyFive() {
        double globalMean = 3.5;
        RatingSummary lone = SCORING.summarise(new RatingTally(5L, 1), globalMean);
        RatingSummary established = SCORING.summarise(new RatingTally(2450L, 500), globalMean); // average 4.9

        assertThat(lone.score()).isLessThan(established.score());
        // A naive average-sort would invert the ranking: the lone vote's own mean is the higher of the two.
        assertThat(lone.average()).isGreaterThan(established.average());
    }

    @Test
    void anUnratedWarpScoresZeroRatherThanTheGlobalMean() {
        RatingSummary summary = SCORING.summarise(RatingTally.empty(), 4.0);

        assertThat(summary.sum()).isZero();
        assertThat(summary.count()).isZero();
        assertThat(summary.average()).isZero();
        assertThat(summary.score()).isZero();
    }

    @Test
    void theScoreSitsNearTheGlobalMeanWithFewVotesAndNearTheAverageWithMany() {
        double globalMean = 3.0;
        RatingSummary few = SCORING.summarise(new RatingTally(5L, 1), globalMean);
        RatingSummary many = SCORING.summarise(new RatingTally(5000L, 1000), globalMean); // average 5.0

        // One five-star vote against a mean of 3.0 with C=10 lands near the mean, well below its own 5.0 average.
        assertThat(few.score()).isCloseTo((10 * 3.0 + 5) / 11.0, within(1e-9));
        // A thousand five-star votes swamp the prior and pull the score up toward the 5.0 average.
        assertThat(many.score()).isGreaterThan(4.9);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 25, 100})
    void addingAnAboveMeanVoteRaisesTheScore(int votes) {
        // Each vote is five stars against a global mean of 2.0, so every extra vote strictly raises the score.
        double globalMean = 2.0;
        RatingSummary fewer = SCORING.summarise(new RatingTally(5L * votes, votes), globalMean);
        RatingSummary more = SCORING.summarise(new RatingTally(5L * (votes + 1), votes + 1), globalMean);

        assertThat(more.score()).isGreaterThan(fewer.score());
    }

    @Test
    void aHigherStarSumOverTheSameVoteCountScoresHigher() {
        double globalMean = 3.0;
        RatingSummary lower = SCORING.summarise(new RatingTally(300L, 100), globalMean);
        RatingSummary higher = SCORING.summarise(new RatingTally(400L, 100), globalMean);

        assertThat(higher.score()).isGreaterThan(lower.score());
        assertThat(higher.average()).isGreaterThan(lower.average());
    }

    @Test
    void zeroConfidenceReducesTheScoreToThePlainAverage() {
        RatingSummary summary = new BayesianRating(0).summarise(new RatingTally(30L, 8), 4.0);

        assertThat(summary.score()).isCloseTo(30.0 / 8.0, within(1e-9));
        assertThat(summary.score()).isCloseTo(summary.average(), within(1e-9));
    }

    @Test
    void negativeConfidenceIsRejected() {
        assertThatThrownBy(() -> new BayesianRating(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
