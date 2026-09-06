package com.uxplima.uxmessentials.ranks.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.RankId;

/**
 * Outbound port for the durable, per-player rank pointer. It stores exactly the raw rank id and prestige a
 * player is on, the queryable facts of the {@code player_ranks} row, so the application decides what happens
 * when the stored id is no longer on the ladder; the port only records and reads. The pointer is DB-backed and
 * survives a world rollback, never PDC (the same hard invariant the economy ledger holds). The jOOQ adapter
 * implements this; the use cases depend only on the contract.
 */
public interface PlayerRankRepository {

    /**
     * The player's stored rank pointer, or empty when they have never been assigned one (a fresh player who has
     * not yet ranked up). An empty result is the signal for the application to default to the ladder's first
     * rank at prestige zero.
     */
    Optional<PlayerRank> find(UUID playerId);

    /**
     * Persist the player's rank pointer, upserting on the player uuid: a re-save overwrites the rank id and
     * prestige in place rather than inserting a second row. This is the single write every rankup and prestige
     * advance goes through.
     */
    void save(UUID playerId, RankId rankId, Prestige prestige);
}
