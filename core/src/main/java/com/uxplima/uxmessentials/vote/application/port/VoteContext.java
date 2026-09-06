package com.uxplima.uxmessentials.vote.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that supplies the boundary facts the {@link com.uxplima.uxmessentials.vote.application.RewardEngine}
 * cannot derive from pure domain: a voter's current world, their permissions, the online check, and the
 * chance roll. {@code HandleVote} builds a {@code RewardContext} from this port plus the recorded tally
 * and the vote's service; the adapter implements it over Bukkit (world + permissions), the player lookup
 * (online), and real RNG (the roll). A test backs it with deterministic stubs.
 *
 * <p>{@link #worldOf(PlayerRef)} returns an empty string for an offline voter. The engine treats that as
 * "no world to satisfy a world filter against", so a world-filtered spec never pays an offline voter.
 */
public interface VoteContext {

    /** The name of the world the voter is currently in, or an empty string when they are offline. */
    String worldOf(PlayerRef voter);

    /** True when {@code voter} holds the permission {@code node}. */
    boolean hasPermission(PlayerRef voter, String node);

    /** True when a {@code 1..100} roll lands at or below {@code chancePercent} (so 100 always passes, 0 never). */
    boolean roll(int chancePercent);

    /** True when the voter is currently connected. */
    boolean isOnline(PlayerRef voter);
}
