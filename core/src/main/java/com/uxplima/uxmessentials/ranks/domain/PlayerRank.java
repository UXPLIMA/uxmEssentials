package com.uxplima.uxmessentials.ranks.domain;

import java.util.Objects;

/**
 * The stored rank pointer for one player: the raw {@link RankId} they are currently on plus their {@link Prestige}.
 * It mirrors the {@code player_ranks} row exactly (a raw id, not a resolved {@link Rank}) so persistence stays
 * decoupled from whatever the current ladder happens to hold. When the ladder no longer contains the stored id
 * (an operator removed a rank), it is the application's job to resolve that gracefully; the pointer itself simply
 * records what the store last wrote.
 *
 * @param rankId the rank the player is on
 * @param prestige the player's prestige level
 */
public record PlayerRank(RankId rankId, Prestige prestige) {

    public PlayerRank {
        Objects.requireNonNull(rankId, "rankId");
        Objects.requireNonNull(prestige, "prestige");
    }
}
