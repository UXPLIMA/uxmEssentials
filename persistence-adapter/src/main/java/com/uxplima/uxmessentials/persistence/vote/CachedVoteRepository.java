package com.uxplima.uxmessentials.persistence.vote;

import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.vote.application.port.ForwardingVoteRepository;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;

/**
 * A thin cache decorator over a delegate {@link VoteRepository} for the one hot read: the global vote-party
 * counter. The counter is read on every received vote and on every {@code /voteparty}, so it is held in an
 * {@link AtomicInteger} that loads through the delegate once (a sentinel of {@code -1} until first read) and
 * is updated write-through on {@link #setPartyCount}. The offline queue and vote totals are not cached
 * queue operations mutate rows (enqueue/drain) and totals are updated on every vote, so they forward straight
 * through {@link ForwardingVoteRepository} to the durable delegate which stays the source of truth. The
 * leaderboard query ({@link #topVoters}) is also uncached, as it is a bounded query that must reflect the
 * latest data.
 */
public final class CachedVoteRepository extends ForwardingVoteRepository {

    private static final int UNLOADED = -1;

    private final AtomicInteger cachedCount = new AtomicInteger(UNLOADED);

    public CachedVoteRepository(VoteRepository delegate) {
        super(delegate);
    }

    @Override
    public int partyCount() {
        int cached = cachedCount.get();
        if (cached != UNLOADED) {
            return cached;
        }
        int loaded = delegate.partyCount();
        cachedCount.set(loaded);
        return loaded;
    }

    @Override
    public void setPartyCount(int count) {
        delegate.setPartyCount(count);
        cachedCount.set(count);
    }

    @Override
    public int incrementAndGetPartyCount() {
        int durable = delegate.incrementAndGetPartyCount();
        // The durable store is the authority for the increment; the cache adopts whatever value it returned.
        cachedCount.set(durable);
        return durable;
    }

    @Override
    public boolean claimPartyFire(int threshold) {
        boolean won = delegate.claimPartyFire(threshold);
        if (won) {
            // The durable store now holds 0; align the cache so subsequent partyCount() reads are correct.
            cachedCount.set(0);
        }
        return won;
    }

    /** Drop the cached counter so the next read reloads it from the database; call on a module reload. */
    public void invalidate() {
        cachedCount.set(UNLOADED);
    }
}
