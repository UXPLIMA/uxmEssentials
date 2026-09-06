package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * Outbound port the access gate delegates to when the warp it just admitted lives on another backend. It replaces
 * the local {@link PlayerWarpTeleporter#teleportTo} hop for a remote warp: the implementation records a
 * {@link PendingTeleport} in the shared store and connects the player across the proxy, or, when the proxy
 * channel is unreachable: notifies the player and refunds the charge, since they never moved.
 *
 * <p>Soft coupling: the gate injects this as an {@link Optional}. When the {@code cross-server} sub-group is off
 * the seam is absent and the gate handles a remote warp itself (notify unavailable and refund) rather than
 * attempting a broken local hop into another server's world.
 */
public interface CrossServerTeleport {

    /**
     * Route {@code who} to the remote {@code warp}. {@code charged} is the exact amount the gate debited for this
     * use (empty for a member, a cost-bypass holder, a free warp, or when no economy is wired), carried through so
     * the arrival, or an unreachable proxy, can refund precisely.
     */
    void send(PlayerRef who, PlayerWarp warp, Optional<WarpCost> charged);
}
