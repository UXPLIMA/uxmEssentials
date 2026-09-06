package com.uxplima.uxmessentials.vote.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A vote was received and credited to an online voter. The per-vote rewards ran and the thank-you was
 * broadcast. A vote for an offline voter is queued instead and raises nothing here; that path is a
 * deferral, not a credited vote.
 *
 * @param voter the player credited with the vote
 * @param service the vote list the vote came from
 */
public record VoteReceived(PlayerRef voter, String service) implements VoteEvent {

    public VoteReceived {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(service, "service");
    }
}
