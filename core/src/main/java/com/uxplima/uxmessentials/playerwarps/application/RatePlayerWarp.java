package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.domain.BayesianRating;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RewardSpec;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp rate <name> <1-5>}: any viewer who can see a warp awards it a star rating, driving the Bayesian
 * {@code rating_score} the "top rated" browse sorts on. A star outside 1..5 is rejected
 * ({@link PlayerWarpError#RATING_INVALID}); a missing warp is {@link PlayerWarpError#NOT_FOUND}; the owner may not
 * rate their own warp ({@link PlayerWarpError#CANNOT_RATE_OWN}). Self-rating from your own account is the cheapest
 * score-boost, so blocking the owner closes the obvious hole and the Bayesian smoothing blunts the rest.
 *
 * <p>A valid vote upserts the rater's star, then recomputes the denormalised rollup from the store's tally and global
 * mean through {@link BayesianRating} and writes it back in one guarded UPDATE, so the sort column never drifts from
 * the vote rows.
 *
 * <p>When the {@code ratings.rewards} sub-group is enabled the vote also grants a configured reward, deduped so it
 * cannot be farmed: the rater is rewarded once per warp (dedup id {@value #RATER_REWARD_ID}, re-rating the same
 * warp grants nothing) and the owner is rewarded once per <em>unique</em> rater (dedup id {@code "rater:<uuid>"}, a
 * different rater triggers a fresh owner grant). With the sub-group disabled the reward collaborators are absent
 * ({@code Optional.empty()}), so nothing is granted and no reward row is written.
 */
public final class RatePlayerWarp {

    private static final String RATER_REWARD_ID = "rate";
    private static final String OWNER_REWARD_PREFIX = "rater:";
    private static final String RATER_KIND = "RATER";
    private static final String OWNER_KIND = "OWNER";

    private final PlayerWarpRepository repository;
    private final WarpRatingStore ratings;
    private final Notifier notifier;
    private final BayesianRating scoring;
    private final Clock clock;
    private final Optional<RatingRewards> rewards;

    public RatePlayerWarp(
            PlayerWarpRepository repository,
            WarpRatingStore ratings,
            Notifier notifier,
            BayesianRating scoring,
            Clock clock,
            Optional<RatingRewards> rewards) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ratings = Objects.requireNonNull(ratings, "ratings");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
    }

    /** Record {@code actor}'s {@code stars} on warp {@code name}, or reject an invalid star, missing warp, or self-rate. */
    public Result<Unit, PlayerWarpError> rate(PlayerRef actor, PlayerWarpName name, int stars) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (stars < 1 || stars > 5) {
            notifier.send(actor, PlayerWarpError.RATING_INVALID.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.RATING_INVALID);
        }
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        if (warp.owner().uuid().equals(actor.uuid())) {
            notifier.send(actor, PlayerWarpError.CANNOT_RATE_OWN.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.CANNOT_RATE_OWN);
        }
        PlayerWarpId id = warp.id().orElseThrow();
        ratings.put(id, actor.uuid(), stars, clock.instant());
        RatingSummary rollup = scoring.summarise(ratings.tally(id), ratings.globalMean());
        repository.updateRating(id, rollup);
        notifier.send(
                actor,
                PlayerwarpsMessageKey.PWARP_RATED,
                Map.of("warp", name.value(), "rating", Integer.toString(stars)));
        rewards.ifPresent(active -> grantRewards(active, actor, warp, id));
        return Result.ok();
    }

    /** Grant the rater and (per unique rater) the owner their configured reward, each deduped through the ledger. */
    private void grantRewards(RatingRewards active, PlayerRef actor, PlayerWarp warp, PlayerWarpId id) {
        Instant now = clock.instant();
        rewardRater(active, actor, warp.name(), id, now);
        rewardOwner(active, actor, warp, id, now);
    }

    /**
     * Reward the rater once per warp: skipped entirely when no rater reward is configured, otherwise the
     * {@value #RATER_REWARD_ID} dedup row is written on the first rating and blocks every later one, so re-rating the
     * same warp grants nothing: the anti-farming invariant. With no reward configured there is nothing to farm, so
     * no dedup row and no message are written either.
     */
    private void rewardRater(RatingRewards active, PlayerRef actor, PlayerWarpName name, PlayerWarpId id, Instant now) {
        RewardSpec reward = active.config().raterReward();
        if (reward.isEmpty() || active.store().hasAwarded(actor.uuid(), id, RATER_REWARD_ID)) {
            return;
        }
        active.granter().grant(actor, reward);
        active.store().record(actor.uuid(), id, RATER_REWARD_ID, RATER_KIND, now);
        notifier.send(actor, PlayerwarpsMessageKey.PWARP_RATE_REWARDED, Map.of("warp", name.value()));
    }

    /**
     * Reward the owner once per unique rater: the dedup id is {@code "rater:<rater-uuid>"}, so a second rating from
     * the same rater grants nothing while a different rater triggers a fresh owner grant. Skipped entirely when no
     * owner reward is configured. The owner may be offline; the notifier simply no-ops in that case.
     */
    private void rewardOwner(RatingRewards active, PlayerRef actor, PlayerWarp warp, PlayerWarpId id, Instant now) {
        RewardSpec reward = active.config().ownerReward();
        if (reward.isEmpty()) {
            return;
        }
        PlayerRef owner = warp.owner();
        String rewardId = OWNER_REWARD_PREFIX + actor.uuid();
        if (active.store().hasAwarded(owner.uuid(), id, rewardId)) {
            return;
        }
        active.granter().grant(owner, reward);
        active.store().record(owner.uuid(), id, rewardId, OWNER_KIND, now);
        notifier.send(
                owner,
                PlayerwarpsMessageKey.PWARP_RATE_REWARD_OWNER,
                Map.of("warp", warp.name().value(), "player", actor.name()));
    }
}
