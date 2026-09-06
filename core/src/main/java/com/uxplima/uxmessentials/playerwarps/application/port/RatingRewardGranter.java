package com.uxplima.uxmessentials.playerwarps.application.port;

import com.uxplima.uxmessentials.playerwarps.domain.RewardSpec;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that actually hands a {@link RewardSpec} to a player: it credits the configured money to
 * {@code subject}'s wallet and dispatches the configured console command with {@code %player%} substituted for the
 * subject's name. The rate use case decides <em>whether</em> and <em>whom</em> to reward (and dedups through
 * {@link WarpRatingRewardStore}); this port only carries out a single grant, so the whole "does money / a command
 * touch Bukkit" surface stays in the adapter.
 *
 * <p>An {@link RewardSpec#isEmpty() empty} spec grants nothing, the implementation short-circuits both arms, so
 * the caller may hand over an unconfigured side of a reward without a guard of its own.
 */
public interface RatingRewardGranter {

    /** Credit {@code spec}'s money and dispatch {@code spec}'s command to {@code subject}; an empty spec is a no-op. */
    void grant(PlayerRef subject, RewardSpec spec);
}
