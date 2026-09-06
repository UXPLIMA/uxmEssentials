package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link RanksPlaceholders} read seam over the ranks read use case: {@link CurrentRank} resolves the player's
 * held rank and prestige from the DB-backed pointer (the same read the {@code /rankup} pipeline and the ladder GUI
 * use), and the parsed {@link RankLadder} answers the next rank up. Both are cheap lookups on the placeholder path
 *: the current standing is one repository read, the next rank a scan of the in-memory ladder.
 */
@NullMarked
public final class StoreRanksPlaceholders implements RanksPlaceholders {

    private final CurrentRank currentRank;
    private final RankLadder ladder;

    public StoreRanksPlaceholders(CurrentRank currentRank, RankLadder ladder) {
        this.currentRank = Objects.requireNonNull(currentRank, "currentRank");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
    }

    @Override
    public Optional<Standing> standing(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return currentRank.of(who.uuid()).map(this::toStanding);
    }

    private Standing toStanding(RankStanding held) {
        Optional<Rank> next = ladder.next(held.rank().id());
        List<Rank> rungs = ladder.ranks();
        return new Standing(
                held.rank().displayName(),
                next.map(Rank::displayName),
                held.prestige().level(),
                position(rungs, held.rank()),
                rungs.size(),
                next.map(rank -> OptionalLong.of(rank.cost())).orElseGet(OptionalLong::empty));
    }

    /** The 1-based rung the held rank sits on, or the first rung when the ladder no longer carries it. */
    private static int position(List<Rank> rungs, Rank held) {
        for (int rung = 0; rung < rungs.size(); rung++) {
            if (rungs.get(rung).id().equals(held.id())) {
                return rung + 1;
            }
        }
        return 1;
    }
}
