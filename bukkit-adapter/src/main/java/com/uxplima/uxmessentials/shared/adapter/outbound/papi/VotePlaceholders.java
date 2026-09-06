package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;

/**
 * Read seam the expansion queries for the {@code votes_*} and {@code voteparty_*} placeholders. It is
 * an adapter over the vote context's existing read ports ({@code VoteRepository.totalsOf} and
 * {@code VotePartyStatus} counter) wired during bootstrap; when the vote module is disabled the seam
 * is absent and the placeholders degrade to the empty/"-" default.
 *
 * <p>The tally reads hit the cached repository so a PAPI request for an online player should see
 * a warm cache (populated by the vote handler). For a cold read the repository falls back to a DB
 * query: acceptable for a placeholder; the value is still correct.
 */
public interface VotePlaceholders {

    /** How many votes {@code who} has accumulated for the given {@code period}. */
    long countFor(PlayerRef who, VotePeriod period);

    /**
     * {@code who}'s current consecutive-day voting streak.
     *
     * <p>The default returns {@code 0} so existing anonymous or minimal implementations in tests do not
     * need to override it until they exercise the streak placeholders.
     */
    default long currentStreak(PlayerRef who) {
        return 0;
    }

    /**
     * {@code who}'s longest consecutive-day voting streak ever reached.
     *
     * <p>The default returns {@code 0} for the same reason as {@link #currentStreak}.
     */
    default long bestStreak(PlayerRef who) {
        return 0;
    }

    /** The current accumulated vote-party counter. */
    int partyCount();

    /** The configured party threshold (votes needed to fire the party). */
    int partyThreshold();

    /**
     * The leaderboard row at 1-based rank {@code rank} for {@code period}, or empty when the rank
     * is out of range (no data or {@code rank < 1} or {@code rank} exceeds the top-N ceiling).
     *
     * <p>The default returns {@link Optional#empty()} so existing anonymous or minimal implementations
     * in tests do not need to override it until they exercise indexed placeholders.
     */
    default Optional<VoteRanking> topAt(VotePeriod period, int rank) {
        return Optional.empty();
    }

    /**
     * The 1-based leaderboard position of {@code who} for {@code period}, or empty when the player
     * does not appear in the top-N results.
     *
     * <p>The default returns {@link OptionalInt#empty()} for the same reason as {@link #topAt}.
     */
    default OptionalInt positionOf(PlayerRef who, VotePeriod period) {
        return OptionalInt.empty();
    }
}
