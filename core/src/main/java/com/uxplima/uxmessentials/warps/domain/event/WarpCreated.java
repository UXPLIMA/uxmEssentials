package com.uxplima.uxmessentials.warps.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * A warp was created: {@code /setwarp} under a name no warp existed at. A {@code /setwarp} onto an
 * existing name re-anchors that warp and raises nothing here; that path is a move, not a creation.
 *
 * @param name the name the warp was created under
 * @param owner the player who created the warp
 * @param location where the new warp points
 */
public record WarpCreated(WarpName name, PlayerRef owner, Position location) implements WarpEvent {

    public WarpCreated {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(location, "location");
    }
}
