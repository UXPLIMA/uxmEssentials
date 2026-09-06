package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * A vote party reached its threshold and fired on the origin backend, so peers should run their local
 * party celebration (broadcast, effects) without re-counting. The shared database holds the authoritative
 * accumulated vote total and the fired stamp; this frame is the notification that lets every backend
 * celebrate the same party once, in lockstep, rather than each backend racing to detect the threshold from
 * its own cached counter. The {@code threshold} travels with the frame for the broadcast and audit context
 * only: the count itself stays in the DB.
 *
 * @param originServer the backend on which the party fired
 * @param threshold the vote count at which the party fired, for broadcast and logging context
 */
public record VotePartyFired(String originServer, int threshold) implements NetworkMessage {

    public VotePartyFired {
        Objects.requireNonNull(originServer, "originServer");
        if (originServer.isBlank()) {
            throw new IllegalArgumentException("originServer must not be blank");
        }
        if (threshold < 0) {
            throw new IllegalArgumentException("threshold must not be negative: " + threshold);
        }
    }

    @Override
    public MessageType type() {
        return MessageType.VOTE_PARTY_FIRED;
    }
}
