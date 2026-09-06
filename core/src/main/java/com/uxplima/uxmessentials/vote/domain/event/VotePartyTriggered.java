package com.uxplima.uxmessentials.vote.domain.event;

/**
 * The accumulated vote count reached the configured threshold and a vote party fired, every online
 * player received the party rewards and the counter was reset to zero.
 *
 * @param threshold the count at which the party fired
 */
public record VotePartyTriggered(int threshold) implements VoteEvent {

    public VotePartyTriggered {
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least one: " + threshold);
        }
    }
}
