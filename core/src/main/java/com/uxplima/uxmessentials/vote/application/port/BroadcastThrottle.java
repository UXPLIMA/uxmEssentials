package com.uxplima.uxmessentials.vote.application.port;

import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over the transient record of when each voter was last announced, backing the
 * {@link com.uxplima.uxmessentials.vote.domain.BroadcastType#COOLDOWN_PER_PLAYER} policy. It holds only
 * the per-voter timestamp, no message content, so it is the plugin's own transient state, not operator
 * data, and may live entirely in memory (a player who has never been announced simply has no entry).
 *
 * <p>The adapter backs this with a {@code ConcurrentHashMap<UUID, Instant>}; reads on the vote-handling
 * path and writes when a broadcast fires are both keyed by the voter, so they never race.
 */
public interface BroadcastThrottle {

    /** When {@code voter} was last announced, or empty if they have never been announced. */
    Optional<Instant> lastBroadcastAt(PlayerRef voter);

    /** Record that {@code voter} was announced at {@code at}, replacing any earlier timestamp. */
    void recordBroadcast(PlayerRef voter, Instant at);
}
