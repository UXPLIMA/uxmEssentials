package com.uxplima.uxmessentials.holograms.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A hologram was created, {@code /hologram create} under a name no hologram existed at.
 *
 * @param name the name the hologram was created under
 * @param creator the player who created the hologram
 * @param location where the new hologram floats
 */
public record HologramCreated(HologramName name, PlayerRef creator, Position location) implements HologramEvent {

    public HologramCreated {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(location, "location");
    }
}
