package com.uxplima.uxmessentials.ranks.domain;

import java.util.List;
import java.util.Objects;

/**
 * One rung of the rank ladder: its identity, the {@code order} that places it relative to its neighbours (lower
 * ranks up first), the operator's display name, the rankup {@code cost}, and the raw requirement and action
 * strings. The requirements and actions stay opaque {@link String}s here. Phase 1 only needs to carry them
 * through from {@code ranks.conf}; the requirement evaluator and the action executor that parse them land in the
 * rankup phase, so the domain does not interpret their grammar yet.
 *
 * <p>The lists are defensively copied so a {@link Rank} is immutable once built, and the cost is non-negative
 * (a rank cannot pay the player to advance). The order is a plain {@code int} so an operator may leave gaps
 * ({@code 10}, {@code 20}, {@code 30}) and insert a rank between two later without renumbering the rest.
 *
 * @param id the rank's identity
 * @param order the ladder position (ascending); lower advances to higher
 * @param displayName the operator-authored display name shown in messages and the GUI
 * @param cost the amount charged through the economy port on rankup ({@code 0} for a free rank)
 * @param requirements the raw requirement strings, parsed by the rankup phase
 * @param actions the raw action strings, run on rankup by the rankup phase
 */
public record Rank(
        RankId id, int order, String displayName, long cost, List<String> requirements, List<String> actions) {

    public Rank {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(actions, "actions");
        if (cost < 0) {
            throw new IllegalArgumentException("rank cost must not be negative: " + cost);
        }
        requirements = List.copyOf(requirements);
        actions = List.copyOf(actions);
    }
}
