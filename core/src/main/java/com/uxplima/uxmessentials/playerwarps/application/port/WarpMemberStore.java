package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;

/**
 * Outbound port for a warp's members (co-owners and managers). The delegates a warp owner grants a management
 * {@link WarpRole}. One row per {@code (warp, player)}, so {@link #put} is an upsert: granting a player a new
 * role overwrites their existing row rather than inserting a second. The ordered access gate (P4-T3) calls
 * {@link #roleOf} to let a member reach a private warp and to decide which management actions they may take.
 */
public interface WarpMemberStore {

    /** Grant the member in {@code member} their role on {@code warp}, upserting on {@code (warp, member.player)}. */
    void put(PlayerWarpId warp, WarpMember member);

    /** Revoke {@code player}'s membership of {@code warp}; a no-op when the player is not a member. */
    void remove(PlayerWarpId warp, UUID player);

    /** The role {@code player} holds on {@code warp}, if they are a member. */
    Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player);

    /** Every member of {@code warp} with their role. */
    List<WarpMember> list(PlayerWarpId warp);
}
