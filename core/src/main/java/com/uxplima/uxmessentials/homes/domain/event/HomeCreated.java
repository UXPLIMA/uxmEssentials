package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A home was created: {@code /sethome} into a slot the owner did not already have a home in. Relocating
 * a home already in a slot raises {@link HomeRelocated}, not this; that path is a move, not a creation.
 *
 * @param owner the player who now owns the home
 * @param slot the slot the home was created in
 * @param location where the new home points
 */
public record HomeCreated(PlayerRef owner, HomeSlot slot, Position location) implements HomeEvent {

    public HomeCreated {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(location, "location");
    }
}
