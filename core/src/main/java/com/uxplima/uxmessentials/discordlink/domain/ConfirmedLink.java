package com.uxplima.uxmessentials.discordlink.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A confirmed binding between a Minecraft account and a Discord user. One row per player (keyed by the account
 * uuid) and the Discord id is unique across rows, so a single Discord account can be bound to at most one
 * player. The {@code /link} confirmation rejects a code when the presenting Discord id is already bound to a
 * different player.
 *
 * @param player the bound Minecraft account
 * @param discordId the bound Discord user
 * @param linkedAt the instant the binding was confirmed
 */
public record ConfirmedLink(UUID player, DiscordId discordId, Instant linkedAt) {

    public ConfirmedLink {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(discordId, "discordId");
        Objects.requireNonNull(linkedAt, "linkedAt");
    }
}
