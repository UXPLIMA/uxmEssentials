package com.uxplima.uxmessentials.playerwarps.application.port;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.RatingTally;

/**
 * Outbound port for the per-vote star ratings of a warp, one row per {@code (warp, player)}, so a player holds at
 * most one live rating on any warp and re-rating overwrites their star in place rather than stacking a second vote.
 * It owns only the vote rows; the denormalised rollup on {@code player_warps} (the sum, count, average, and the
 * Bayesian score the browse sorts on) is the rate use case's concern, recomputed from this store's {@link #tally}
 * and {@link #globalMean} after every vote.
 */
public interface WarpRatingStore {

    /** Record {@code player}'s {@code stars} on {@code warp} at {@code at}, overwriting their prior vote (upsert). */
    void put(PlayerWarpId warp, UUID player, int stars, Instant at);

    /** The raw star {@code sum} and vote {@code count} for {@code warp}; {@code (0, 0)} when nobody has rated it. */
    RatingTally tally(PlayerWarpId warp);

    /** The mean star across every rating on the server, for the Bayesian prior; {@code 0.0} when there are none. */
    double globalMean();
}
