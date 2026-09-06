package com.uxplima.uxmessentials.playerwarps.support;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;

/**
 * A {@link WarpMemberStore} with no members, shared by the player-warp GUI and command tests that build a
 * {@code WarpAuthorization} over it. With no member rows, authority collapses to ownership alone. The owner is the
 * only actor a warp admits, which is exactly what those tests exercise (an owner archives their own warp).
 */
public final class NoWarpMembers implements WarpMemberStore {

    @Override
    public void put(PlayerWarpId warp, WarpMember member) {}

    @Override
    public void remove(PlayerWarpId warp, UUID player) {}

    @Override
    public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
        return Optional.empty();
    }

    @Override
    public List<WarpMember> list(PlayerWarpId warp) {
        return List.of();
    }
}
