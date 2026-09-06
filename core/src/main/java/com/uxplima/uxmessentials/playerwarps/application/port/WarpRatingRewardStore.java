package com.uxplima.uxmessentials.playerwarps.application.port;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;

/**
 * Outbound port for the rating-reward ledger, one row per {@code (subject, warp, rewardId)}, the dedup key that
 * makes a rating reward un-farmable. The rate use case checks {@link #hasAwarded} before granting and
 * {@link #record}s the grant after, so a reward already handed out for a given {@code rewardId} is never handed
 * out twice: the rater's {@code "rate"} id fires once per warp (re-rating grants nothing), the owner's
 * {@code "rater:<uuid>"} id fires once per unique rater on their warp.
 *
 * <p>This store owns only the ledger rows; it never touches money or dispatches a command: that is the
 * {@link RatingRewardGranter}'s concern. Backs the {@code player_warp_rating_rewards} side table.
 */
public interface WarpRatingRewardStore {

    /** Whether {@code subject} has already been awarded {@code rewardId} for {@code warp}. */
    boolean hasAwarded(UUID subject, PlayerWarpId warp, String rewardId);

    /**
     * Record that {@code subject} was awarded {@code rewardId} of {@code kind} for {@code warp} at {@code at}.
     * Idempotent: a second record of the same {@code (subject, warp, rewardId)} is a silent no-op (the primary key
     * dedups), so the ledger never carries a duplicate grant.
     */
    void record(UUID subject, PlayerWarpId warp, String rewardId, String kind, Instant at);
}
