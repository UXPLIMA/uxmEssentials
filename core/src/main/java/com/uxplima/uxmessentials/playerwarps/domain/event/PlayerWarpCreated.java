package com.uxplima.uxmessentials.playerwarps.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A player-warp was created: {@code /setpwarp} under a name the owner had no warp at. A {@code /setpwarp}
 * onto an existing name re-anchors that warp and raises nothing here; that path is a move, not a creation.
 *
 * @param owner the player who created the warp
 * @param name the name the warp was created under
 * @param location where the new warp points
 */
public record PlayerWarpCreated(PlayerRef owner, PlayerWarpName name, Position location) implements PlayerWarpEvent {

    public PlayerWarpCreated {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
    }
}
