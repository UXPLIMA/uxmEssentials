package com.uxplima.uxmessentials.holograms.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A hologram was removed, {@code /hologram delete}. The name is freed so a later {@code /hologram create}
 * may reuse it.
 *
 * @param name the name of the removed hologram
 * @param removedBy the player who removed the hologram (audit attribution)
 */
public record HologramDeleted(HologramName name, PlayerRef removedBy) implements HologramEvent {

    public HologramDeleted {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(removedBy, "removedBy");
    }
}
