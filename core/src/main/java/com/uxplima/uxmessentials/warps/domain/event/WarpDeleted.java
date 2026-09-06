package com.uxplima.uxmessentials.warps.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * A warp was removed, {@code /delwarp}. The name is freed so a later {@code /setwarp} may reuse it.
 *
 * @param name the name of the removed warp
 * @param removedBy the player who removed the warp (audit attribution)
 */
public record WarpDeleted(WarpName name, PlayerRef removedBy) implements WarpEvent {

    public WarpDeleted {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(removedBy, "removedBy");
    }
}
