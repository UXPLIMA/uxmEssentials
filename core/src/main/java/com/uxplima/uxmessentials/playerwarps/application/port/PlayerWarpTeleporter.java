package com.uxplima.uxmessentials.playerwarps.application.port;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port the player-warps context uses to <em>delegate</em> teleport execution to the teleport
 * context. Player-warps never re-implements movement: it resolves which {@link PlayerWarp} a player asked for
 * and gates access (ownership, then the public flag), then hands the warp off here, and the adapter behind
 * this port drives the teleport context's gated machinery (the shared cooldown, the move-cancellable warmup,
 * the region-aware async hop). This keeps the cooldown/warmup invariant owned in one place, the teleport
 * context, while player-warps owns only the aggregate and its access gates.
 */
public interface PlayerWarpTeleporter {

    /** Send {@code who} to {@code warp}, routing the hop through the teleport context's gated flow. */
    void teleportTo(PlayerRef who, PlayerWarp warp);
}
