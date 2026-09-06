package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A home's GUI icon changed. The cosmetic {@link com.uxplima.uxmessentials.homes.domain.HomeIcon} set,
 * cleared, or replaced. The slot is the home's identity and is unchanged; only the icon the menu renders
 * moved.
 *
 * @param owner the player whose home icon changed
 * @param slot the slot whose icon changed
 */
public record HomeIconChanged(PlayerRef owner, HomeSlot slot) implements HomeEvent {

    public HomeIconChanged {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
    }
}
