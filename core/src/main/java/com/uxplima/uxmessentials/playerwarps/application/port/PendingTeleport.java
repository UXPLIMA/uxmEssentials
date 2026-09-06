package com.uxplima.uxmessentials.playerwarps.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * One in-flight cross-server teleport: the origin backend charged the visitor (if the warp was priced), recorded
 * this row in the network-shared {@code player_warp_pending_teleports} table, and handed the player to the proxy;
 * the target backend reads the row on join and completes the hop, or refunds and clears it when the warp is no
 * longer reachable. There is at most one pending teleport per player, so the row is keyed by {@code player}.
 *
 * <p>The charged amount is carried in this context's own terms, a {@link WarpCost} of a {@code BigDecimal} amount
 * plus a {@code currencyId} string, never an economy {@code Money}, so the narrow economy seam is preserved and
 * {@code :core} takes on no economy import. It is {@link Optional#empty()} when nothing was charged (a member, a
 * cost-bypass holder, a free warp, or no economy provider), so the arrival refund is exact.
 *
 * @param player the visitor being routed
 * @param warp the surrogate id of the warp they are heading to
 * @param targetServer the {@code network.server-id} of the backend the warp lives on
 * @param originServer the {@code network.server-id} of the backend that recorded the request
 * @param requestedAt when the origin recorded the request, for the request-ttl expiry check
 * @param paid the amount debited on the origin, to refund if arrival fails; empty when nothing was charged
 */
public record PendingTeleport(
        UUID player,
        PlayerWarpId warp,
        String targetServer,
        String originServer,
        Instant requestedAt,
        Optional<WarpCost> paid) {

    public PendingTeleport {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(paid, "paid");
    }
}
