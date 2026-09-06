package com.uxplima.uxmessentials.homes.application.port;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port the homes context uses to <em>delegate</em> teleport execution to the teleport context.
 * Homes never re-implements movement: it resolves which {@link Home} a player asked for, then hands the
 * home off here, and the adapter behind this port drives the teleport context's gated machinery (the
 * shared cooldown, the move-cancellable warmup, the region-aware async hop). This keeps the
 * cooldown/warmup invariant owned in one place, the teleport context, while homes owns only the
 * aggregate and its quota.
 */
public interface HomeTeleporter {

    /** Send {@code who} to {@code home}, routing the hop through the teleport context's gated flow. */
    void teleportTo(PlayerRef who, Home home);
}
