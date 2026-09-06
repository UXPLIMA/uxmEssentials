package com.uxplima.uxmessentials.homes.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Reads the guest list of one of an owner's homes: the UUIDs the owner has invited to the home in a slot.
 * The adapter resolves those UUIDs to names when it renders the list; this use case only exposes the raw
 * identities so the application layer never touches a player handle.
 */
public final class ListHomeInvites {

    private final HomeInviteRepository invites;

    public ListHomeInvites(HomeInviteRepository invites) {
        this.invites = Objects.requireNonNull(invites, "invites");
    }

    /** The UUIDs invited to {@code owner}'s home in {@code slot}; empty when none. */
    public Set<UUID> of(PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        return invites.invites(owner, slot);
    }
}
