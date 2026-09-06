package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A home was re-anchored to a new position while staying in the same slot, {@code /sethome} run again on
 * an occupied slot, or an explicit relocate. The slot and the owner are unchanged; only where the home
 * points moved.
 *
 * @param owner the player whose home moved
 * @param slot the slot whose home was re-anchored
 */
public record HomeRelocated(PlayerRef owner, HomeSlot slot) implements HomeEvent {

    public HomeRelocated {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
    }
}
