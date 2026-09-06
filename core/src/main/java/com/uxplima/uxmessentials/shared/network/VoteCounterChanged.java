package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * The accumulated vote-party counter advanced on the origin backend (a vote landed), so peers must drop any
 * cached view of the running total and re-read the authoritative row before they show or act on it. The
 * counter is server-wide rather than per-player. Like {@link WarpChanged}, the whole cached value is the
 * unit a peer invalidates, and the durable total lives in the shared database; this frame carries no figure,
 * only the signal to re-read.
 *
 * @param originServer the backend that advanced the counter
 */
public record VoteCounterChanged(String originServer) implements NetworkMessage {

    public VoteCounterChanged {
        Objects.requireNonNull(originServer, "originServer");
        if (originServer.isBlank()) {
            throw new IllegalArgumentException("originServer must not be blank");
        }
    }

    @Override
    public MessageType type() {
        return MessageType.VOTE_COUNTER_CHANGED;
    }
}
