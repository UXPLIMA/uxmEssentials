package com.uxplima.uxmessentials.vote.application;

import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.VoteTally;

/**
 * Everything the {@link RewardEngine} needs to evaluate one vote's reward sets, gathered at the boundary
 * so the engine itself stays a pure function: who voted and whether they are online, the world they are
 * in (empty for an offline voter), the vote-list service the vote came from, the voter's totals after
 * this vote was recorded, whether this vote advanced their consecutive-day streak, and the three gate
 * seams the adapter supplies, a permission check, and a chance roll.
 *
 * <p>{@code rollPasses.test(chancePercent)} returns true when a {@code 1..100} roll lands at or below the
 * spec's chance; the adapter backs it with real RNG and a test backs it with a deterministic stub.
 * {@code hasPermission.test(node)} is the voter's permission check. {@code worldName} is empty for an
 * offline voter, which the engine treats as "no world to satisfy a world filter against".
 * {@code streakAdvanced} is true only when this vote moved the streak to a new day, so streak rewards
 * never re-fire on a same-day re-vote.
 *
 * @param voter the player credited with the vote
 * @param online whether the voter is connected (and so can receive live messages/items)
 * @param worldName the voter's current world, or empty when offline
 * @param service the vote-list service the vote came from, for per-site matching
 * @param totalsAfter the voter's vote totals after this vote was recorded
 * @param streakAdvanced whether this vote moved the consecutive-day streak to a new day
 * @param hasPermission tests whether the voter holds a permission node
 * @param rollPasses tests whether a chance percentage passes its roll
 */
public record RewardContext(
        PlayerRef voter,
        boolean online,
        String worldName,
        String service,
        VoteTally totalsAfter,
        boolean streakAdvanced,
        Predicate<String> hasPermission,
        IntPredicate rollPasses) {

    public RewardContext {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(totalsAfter, "totalsAfter");
        Objects.requireNonNull(hasPermission, "hasPermission");
        Objects.requireNonNull(rollPasses, "rollPasses");
    }
}
