package com.uxplima.uxmessentials.warps.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.Warp;

/**
 * Outbound port the warps context uses to <em>delegate</em> teleport execution to the teleport context.
 * Warps never re-implements movement: it resolves which {@link Warp} a player asked for and gates access
 * (permission, then cost), then hands the warp off here, and the adapter behind this port drives the
 * teleport context's gated machinery (the shared cooldown, the move-cancellable warmup, the region-aware
 * async hop). This keeps the cooldown/warmup invariant owned in one place, the teleport context, while
 * warps owns only the aggregate, its access gates, and the optional cost.
 */
public interface WarpTeleporter {

    /** Send {@code who} to {@code warp}, routing the hop through the teleport context's gated flow. */
    void teleportTo(PlayerRef who, Warp warp);
}
