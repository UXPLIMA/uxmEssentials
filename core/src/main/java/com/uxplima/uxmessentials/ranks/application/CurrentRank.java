package com.uxplima.uxmessentials.ranks.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;

/**
 * Read use case: resolve a player's current {@link RankStanding}, the ladder {@link Rank} they hold and their
 * {@link Prestige}: from the stored pointer.
 *
 * <p>The rules are deliberately forgiving so the read never fails on operator config drift: a player with no
 * stored pointer stands on the ladder's first rank at prestige zero; a player whose stored id is still on the
 * ladder resolves to that rank; a player whose stored id was removed from the ladder falls back to the first
 * rank but keeps their prestige. The only case that yields no standing is an empty ladder. There is no rank to
 * stand on, which callers surface as "ranks not configured" rather than a crash.
 */
public final class CurrentRank {

    private final PlayerRankRepository repository;
    private final RankLadder ladder;

    public CurrentRank(PlayerRankRepository repository, RankLadder ladder) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
    }

    /** The player's resolved standing, or empty only when the ladder holds no ranks. */
    public Optional<RankStanding> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<PlayerRank> stored = repository.find(playerId);
        if (stored.isEmpty()) {
            return ladder.first().map(rank -> new RankStanding(rank, Prestige.INITIAL));
        }
        PlayerRank pointer = stored.get();
        Optional<Rank> onLadder = ladder.rank(pointer.rankId());
        if (onLadder.isPresent()) {
            return Optional.of(new RankStanding(onLadder.get(), pointer.prestige()));
        }
        return ladder.first().map(rank -> new RankStanding(rank, pointer.prestige()));
    }
}
