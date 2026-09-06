package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.RatingRewardGranter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingRewardStore;
import com.uxplima.uxmessentials.playerwarps.domain.BayesianRating;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RewardSpec;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The reward step of the rate use case over in-memory fakes: a rating grants the rater their configured reward once
 * per warp (re-rating grants nothing. The anti-farming dedup), the owner is rewarded once per <em>unique</em>
 * rater, and with the sub-group disabled nothing is granted and no ledger row is written.
 */
class RatePlayerWarpRewardsTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");
    private static final RewardSpec RATER_REWARD = RewardSpec.of(BigDecimal.TEN, "default", "");
    private static final RewardSpec OWNER_REWARD = RewardSpec.of(BigDecimal.valueOf(5L), "default", "");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Ratings ratings;
    private PlayerWarpTestSupport.Sink sink;
    private RewardLedger ledger;
    private RecordingGranter granter;
    private PlayerRef owner;
    private PlayerWarpId warpId;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        ratings = new PlayerWarpTestSupport.Ratings();
        sink = new PlayerWarpTestSupport.Sink();
        ledger = new RewardLedger();
        granter = new RecordingGranter();
        owner = PlayerWarpTestSupport.ref("Owner");
        warpId = repository.put(PlayerWarpTestSupport.warp(owner, "hub")).id().orElseThrow();
    }

    private RatePlayerWarp rate(RatingRewardConfig config) {
        return new RatePlayerWarp(
                repository,
                ratings,
                PlayerWarpTestSupport.notifier(sink),
                new BayesianRating(10),
                PlayerWarpTestSupport.CLOCK,
                Optional.of(new RatingRewards(ledger, granter, config)));
    }

    @Test
    void aRatingGrantsTheRaterTheConfiguredRewardOnceAndRecordsTheRow() {
        PlayerRef rater = PlayerWarpTestSupport.ref("Rater");
        RatePlayerWarp rate = rate(new RatingRewardConfig(true, RATER_REWARD, RewardSpec.none()));

        rate.rate(rater, HUB, 5);

        assertThat(granter.grants).containsExactly(new RecordingGranter.Grant(rater.uuid(), RATER_REWARD));
        assertThat(ledger.recorded).containsExactly(new RewardLedger.Recorded(rater.uuid(), warpId, "rate", "RATER"));
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.rate-rewarded"));
    }

    @Test
    void aSecondRatingOfTheSameWarpDoesNotReRewardTheRater() {
        PlayerRef rater = PlayerWarpTestSupport.ref("Rater");
        RatePlayerWarp rate = rate(new RatingRewardConfig(true, RATER_REWARD, RewardSpec.none()));

        rate.rate(rater, HUB, 1);
        rate.rate(rater, HUB, 5);

        assertThat(granter.grants).hasSize(1);
        assertThat(ledger.rows).hasSize(1);
    }

    @Test
    void theOwnerIsRewardedOncePerUniqueRater() {
        PlayerRef first = PlayerWarpTestSupport.ref("First");
        PlayerRef second = PlayerWarpTestSupport.ref("Second");
        RatePlayerWarp rate = rate(new RatingRewardConfig(true, RewardSpec.none(), OWNER_REWARD));

        rate.rate(first, HUB, 5);
        rate.rate(first, HUB, 4); // same rater re-rating grants the owner nothing more
        rate.rate(second, HUB, 3);

        assertThat(granter.grants)
                .containsExactly(
                        new RecordingGranter.Grant(owner.uuid(), OWNER_REWARD),
                        new RecordingGranter.Grant(owner.uuid(), OWNER_REWARD));
        assertThat(ledger.recorded)
                .containsExactly(
                        new RewardLedger.Recorded(owner.uuid(), warpId, "rater:" + first.uuid(), "OWNER"),
                        new RewardLedger.Recorded(owner.uuid(), warpId, "rater:" + second.uuid(), "OWNER"));
    }

    @Test
    void aDisabledSubGroupGrantsNothingAndRecordsNoRow() {
        PlayerRef rater = PlayerWarpTestSupport.ref("Rater");
        RatePlayerWarp rate = new RatePlayerWarp(
                repository,
                ratings,
                PlayerWarpTestSupport.notifier(sink),
                new BayesianRating(10),
                PlayerWarpTestSupport.CLOCK,
                Optional.empty());

        rate.rate(rater, HUB, 5);

        assertThat(granter.grants).isEmpty();
        assertThat(ledger.rows).isEmpty();
    }

    /** An in-memory reward ledger: the row set dedups, and the ordered list preserves what was recorded. */
    static final class RewardLedger implements WarpRatingRewardStore {
        record Row(UUID subject, PlayerWarpId warp, String rewardId) {}

        record Recorded(UUID subject, PlayerWarpId warp, String rewardId, String kind) {}

        final Set<Row> rows = new LinkedHashSet<>();
        final List<Recorded> recorded = new ArrayList<>();

        @Override
        public boolean hasAwarded(UUID subject, PlayerWarpId warp, String rewardId) {
            return rows.contains(new Row(subject, warp, rewardId));
        }

        @Override
        public void record(UUID subject, PlayerWarpId warp, String rewardId, String kind, Instant at) {
            if (rows.add(new Row(subject, warp, rewardId))) {
                recorded.add(new Recorded(subject, warp, rewardId, kind));
            }
        }
    }

    /** A granter that records every grant it is handed, so a test can assert the subject and the spec. */
    static final class RecordingGranter implements RatingRewardGranter {
        record Grant(UUID subject, RewardSpec spec) {}

        final List<Grant> grants = new ArrayList<>();

        @Override
        public void grant(PlayerRef subject, RewardSpec spec) {
            grants.add(new Grant(subject.uuid(), spec));
        }
    }
}
