package com.uxplima.uxmessentials.persistence.vote;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the vote context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link VoteRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the jOOQ adapter behind a thin counter cache, write-through at
 * the database, the hot party counter served from memory.
 */
@NullMarked
public final class VoteRepositories {

    private VoteRepositories() {}

    /** A counter-cached jOOQ {@link VoteRepository} over the shared persistence DSL. */
    public static VoteRepository cached(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedVoteRepository(new JooqVoteRepository(persistence.dsl()));
    }

    /**
     * The counter-cached jOOQ {@link VoteRepository} as its concrete decorator type, so the wiring can hand
     * the cross-server bus an invalidation hook on the same counter cache the vote commands read, a remote
     * counter advance or a remote party fire drops the cached running total. Same backing as {@link #cached};
     * this overload exposes the decorator only so the invalidation seam can reach
     * {@link CachedVoteRepository#invalidate()}.
     */
    public static CachedVoteRepository cachedConcrete(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedVoteRepository(new JooqVoteRepository(persistence.dsl()));
    }
}
