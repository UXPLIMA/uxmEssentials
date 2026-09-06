package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /uninvite <player> [slot]}: revoke a previously granted invite to one of the owner's homes. Also
 * reachable by clicking an invited player's entry in the invited-players GUI. The call is idempotent
 * revoking an invite that was never granted is a no-op that still reports success, so the owner gets the
 * same feedback whether or not the player was on the guest list.
 */
public final class UninviteFromHome {

    private final HomeInviteRepository invites;
    private final Notifier notifier;

    public UninviteFromHome(HomeInviteRepository invites, Notifier notifier) {
        this.invites = Objects.requireNonNull(invites, "invites");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Revoke {@code invited}'s access to {@code owner}'s home in {@code slot}. */
    public void uninvite(PlayerRef owner, HomeSlot slot, PlayerRef invited) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(invited, "invited");
        invites.removeInvite(owner, slot, invited.uuid());
        notifier.send(
                owner,
                HomesMessageKey.HOME_UNINVITED,
                Map.of("player", invited.name(), "slot", Integer.toString(slot.displayNumber())));
    }
}
