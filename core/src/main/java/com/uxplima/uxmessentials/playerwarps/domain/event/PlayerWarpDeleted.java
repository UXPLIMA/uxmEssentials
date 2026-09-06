package com.uxplima.uxmessentials.playerwarps.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player-warp was removed, {@code /pwarp del}. The name is freed so a later {@code /setpwarp} by the same
 * owner may reuse it.
 *
 * @param owner the player whose warp was removed
 * @param name the name of the removed warp
 */
public record PlayerWarpDeleted(PlayerRef owner, PlayerWarpName name) implements PlayerWarpEvent {

    public PlayerWarpDeleted {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
    }
}
