package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A home's display label changed. The cosmetic {@link com.uxplima.uxmessentials.homes.domain.HomeLabel}
 * set, cleared, or replaced. The slot is the home's identity and is unchanged; only the label the player
 * sees moved.
 *
 * @param owner the player whose home was relabelled
 * @param slot the slot whose label changed
 */
public record HomeRenamed(PlayerRef owner, HomeSlot slot) implements HomeEvent {

    public HomeRenamed {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
    }
}
