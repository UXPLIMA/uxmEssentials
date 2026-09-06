package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.RewardSpec;

/**
 * The rating-reward sub-group's tunables, loaded once on enable and swapped atomically on reload (the config-record
 * idiom, mirroring {@link RentConfig} and {@link SponsorConfig}). When {@link #enabled} a warp rating grants
 * {@link #raterReward} to the rater (once per warp) and {@link #ownerReward} to the owner (once per unique rater).
 * Either side may be {@link RewardSpec#isEmpty() empty}, so an operator can reward only the rater, only the owner,
 * or both. A {@link #disabled()} config is the shipped default: the sub-group off, nothing granted.
 *
 * @param enabled whether the rating-reward sub-group runs at all
 * @param raterReward what the rater receives, once per warp they rate
 * @param ownerReward what the owner receives, once per unique rater on their warp
 */
public record RatingRewardConfig(boolean enabled, RewardSpec raterReward, RewardSpec ownerReward) {

    public RatingRewardConfig {
        Objects.requireNonNull(raterReward, "raterReward");
        Objects.requireNonNull(ownerReward, "ownerReward");
    }

    /** The shipped default: the rating-reward sub-group off, so no rating ever grants a reward. */
    public static RatingRewardConfig disabled() {
        return new RatingRewardConfig(false, RewardSpec.none(), RewardSpec.none());
    }
}
