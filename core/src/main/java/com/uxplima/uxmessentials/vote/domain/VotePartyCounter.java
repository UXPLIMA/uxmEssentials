package com.uxplima.uxmessentials.vote.domain;

/**
 * The vote-party progress counter: how many votes have accumulated towards the next party, and the
 * threshold that triggers one. A pure value object, increments produce a new counter, the threshold
 * check is a comparison, and a reset returns the count to zero. The durable count lives in the
 * repository; this record is the in-memory arithmetic the {@code HandleVote} use case runs on each
 * received vote.
 *
 * @param count how many votes have accumulated since the last party (never negative)
 * @param threshold the count at which a party fires (always at least one)
 */
public record VotePartyCounter(int count, int threshold) {

    public VotePartyCounter {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least one: " + threshold);
        }
    }

    /** A counter one vote further along, against the same threshold. */
    public VotePartyCounter incremented() {
        return new VotePartyCounter(count + 1, threshold);
    }

    /** True once the accumulated count has reached (or passed) the threshold: a party is due. */
    public boolean isReached() {
        return count >= threshold;
    }

    /** A fresh counter back at zero against the same threshold, for after a party has fired. */
    public VotePartyCounter reset() {
        return new VotePartyCounter(0, threshold);
    }
}
