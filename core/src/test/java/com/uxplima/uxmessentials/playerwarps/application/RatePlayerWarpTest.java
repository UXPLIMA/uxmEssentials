package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.BayesianRating;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rate use case end-to-end over the in-memory fakes: a valid vote upserts the rater's star and writes back the
 * recomputed Bayesian rollup, a re-rate overwrites rather than stacks, and the owner-self-rate, out-of-range, and
 * missing-warp guards short-circuit before any rollup is written.
 */
class RatePlayerWarpTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");
    private static final PlayerWarpId FOREIGN = PlayerWarpId.of(99L);

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Ratings ratings;
    private PlayerWarpTestSupport.Sink sink;
    private RatePlayerWarp rate;
    private PlayerRef owner;
    private PlayerRef rater;
    private PlayerWarpId warpId;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        ratings = new PlayerWarpTestSupport.Ratings();
        sink = new PlayerWarpTestSupport.Sink();
        rate = new RatePlayerWarp(
                repository,
                ratings,
                PlayerWarpTestSupport.notifier(sink),
                new BayesianRating(10),
                PlayerWarpTestSupport.CLOCK,
                java.util.Optional.empty());
        owner = PlayerWarpTestSupport.ref("Owner");
        rater = PlayerWarpTestSupport.ref("Rater");
        warpId = repository.put(PlayerWarpTestSupport.warp(owner, "hub")).id().orElseThrow();
    }

    @Test
    void aVoteRecordsTheStarAndWritesBackTheBayesianRollup() {
        // Three foreign 2-star votes drag the global mean down, so this lone 5-star vote scores well below its own
        // 5.0 average: exactly the smoothing the browse sort relies on.
        ratings.seed(FOREIGN, UUID.randomUUID(), 2);
        ratings.seed(FOREIGN, UUID.randomUUID(), 2);
        ratings.seed(FOREIGN, UUID.randomUUID(), 2);

        Result<Unit, PlayerWarpError> result = rate.rate(rater, HUB, 5);

        assertThat(result.isOk()).isTrue();
        RatingSummary rollup = Objects.requireNonNull(repository.ratingUpdates.get(warpId));
        assertThat(rollup.sum()).isEqualTo(5L);
        assertThat(rollup.count()).isEqualTo(1);
        assertThat(rollup.average()).isCloseTo(5.0, within(1e-9));
        double globalMean = 11.0 / 4.0; // three foreign 2-star votes + this 5-star vote
        assertThat(rollup.score()).isCloseTo((10 * globalMean + 5) / 11.0, within(1e-9));
        assertThat(rollup.score()).isLessThan(rollup.average());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.rated"));
    }

    @Test
    void reRatingUpdatesTheSingleVoteRatherThanStackingASecond() {
        rate.rate(rater, HUB, 1);
        rate.rate(rater, HUB, 5);

        RatingSummary rollup = Objects.requireNonNull(repository.ratingUpdates.get(warpId));
        assertThat(rollup.count()).isEqualTo(1);
        assertThat(rollup.sum()).isEqualTo(5L);
    }

    @Test
    void theOwnerMayNotRateTheirOwnWarp() {
        Result<Unit, PlayerWarpError> result = rate.rate(owner, HUB, 5);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.CANNOT_RATE_OWN);
        assertThat(repository.ratingUpdates).isEmpty();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.cannot-rate-own"));
    }

    @Test
    void aStarOutsideOneToFiveIsRejectedBeforeAnyVoteIsRecorded() {
        assertThat(rate.rate(rater, HUB, 0).errorOrThrow()).isEqualTo(PlayerWarpError.RATING_INVALID);
        assertThat(rate.rate(rater, HUB, 6).errorOrThrow()).isEqualTo(PlayerWarpError.RATING_INVALID);
        assertThat(repository.ratingUpdates).isEmpty();
    }

    @Test
    void ratingAMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result = rate.rate(rater, PlayerWarpName.of("ghost"), 5);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
        assertThat(repository.ratingUpdates).isEmpty();
    }
}
