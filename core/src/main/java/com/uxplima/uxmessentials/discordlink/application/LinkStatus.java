package com.uxplima.uxmessentials.discordlink.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Reports a player's current binding ({@code /discordlink status}): the confirmed link when one exists, or
 * empty when the player has not linked. A pure read: the command adapter renders the linked/unlinked message.
 */
public final class LinkStatus {

    private final DiscordLinkStore store;

    public LinkStatus(DiscordLinkStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** The player's confirmed binding, if any. */
    public Optional<ConfirmedLink> status(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return store.findByPlayer(player.uuid());
    }
}
