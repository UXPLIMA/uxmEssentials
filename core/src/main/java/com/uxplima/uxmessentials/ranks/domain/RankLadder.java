package com.uxplima.uxmessentials.ranks.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The ordered chain of ranks an operator authored in {@code ranks.conf}. The ladder is the single source of
 * truth for progression order. It sorts its ranks by {@link Rank#order()} once at construction and answers the
 * pure questions the rankup, prestige and GUI phases ask: what is the {@link #first() first} rank, what is the
 * {@link #next(RankId) next} rank after one, does it {@link #contains(RankId) contain} a rank, and what remains
 * {@link #orderedFrom(RankId) from} a rank onward.
 *
 * <p>Rank ids are unique (two rungs with the same id would make {@code next} ambiguous) so a duplicate id is
 * rejected at construction. The ladder may be empty (an operator who cleared {@code ranks.conf}); {@link #first()}
 * then answers empty rather than throwing, and callers decide how to degrade.
 */
public final class RankLadder {

    private final List<Rank> ranks;

    private RankLadder(List<Rank> ranks) {
        this.ranks = ranks;
    }

    /**
     * Build a ladder from the given ranks, sorted ascending by {@link Rank#order()}. Rejects a duplicate rank
     * id so progression stays unambiguous.
     */
    public static RankLadder of(List<Rank> ranks) {
        Objects.requireNonNull(ranks, "ranks");
        Set<RankId> seen = new HashSet<>();
        for (Rank rank : ranks) {
            Objects.requireNonNull(rank, "rank");
            if (!seen.add(rank.id())) {
                throw new IllegalArgumentException("duplicate rank id: " + rank.id());
            }
        }
        List<Rank> sorted = new ArrayList<>(ranks);
        sorted.sort(Comparator.comparingInt(Rank::order));
        return new RankLadder(List.copyOf(sorted));
    }

    /** The lowest rank on the ladder, or empty when the ladder holds no ranks. */
    public Optional<Rank> first() {
        return ranks.isEmpty() ? Optional.empty() : Optional.of(ranks.get(0));
    }

    /** The rank with this id, or empty when it is not on the ladder (e.g. an operator removed it). */
    public Optional<Rank> rank(RankId id) {
        Objects.requireNonNull(id, "id");
        return ranks.stream().filter(rank -> rank.id().equals(id)).findFirst();
    }

    /** Whether the ladder holds a rank with this id. */
    public boolean contains(RankId id) {
        return rank(id).isPresent();
    }

    /** The rank immediately above the one with this id, or empty when it is the top rank or is not on the ladder. */
    public Optional<Rank> next(RankId id) {
        Objects.requireNonNull(id, "id");
        int index = indexOf(id);
        if (index < 0 || index + 1 >= ranks.size()) {
            return Optional.empty();
        }
        return Optional.of(ranks.get(index + 1));
    }

    /** The ranks from the one with this id (inclusive) to the top, in order; empty when the id is not on the ladder. */
    public List<Rank> orderedFrom(RankId id) {
        Objects.requireNonNull(id, "id");
        int index = indexOf(id);
        return index < 0 ? List.of() : List.copyOf(ranks.subList(index, ranks.size()));
    }

    /** Every rank in ascending order, the whole ladder as an immutable list. */
    public List<Rank> ranks() {
        return ranks;
    }

    /** Whether the ladder holds no ranks. */
    public boolean isEmpty() {
        return ranks.isEmpty();
    }

    private int indexOf(RankId id) {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
