package com.uxplima.uxmessentials.discordlink.domain;

import com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey;

/**
 * The modelled failures a discord-link operation can produce. Each value carries the
 * {@link DiscordlinkMessageKey} the in-game adapter renders, so a use case returns a
 * {@code Result.err(DiscordLinkError.X)} and the caller never re-derives the message, the failure reason and
 * its localized text never drift apart.
 *
 * <p>The {@code /link} confirmation runs inside Discord, not on a Minecraft player, so its outcomes are mapped
 * to the bridge's own ephemeral replies at the seam rather than to these player-locale keys; these are the
 * failures the in-game {@code /discordunlink} surfaces, plus the ones the confirmation use case returns for the
 * seam to translate.
 */
public enum DiscordLinkError {

    /** {@code /link} with a code no pending row exists for. */
    NO_PENDING_CODE(DiscordlinkMessageKey.DISCORD_LINK_NOT_LINKED),

    /** {@code /link} with a code whose window has already closed; the stale row is cleared. */
    CODE_EXPIRED(DiscordlinkMessageKey.DISCORD_LINK_NOT_LINKED),

    /** {@code /link} when the presenting Discord account is already bound to a different player. */
    DISCORD_ALREADY_LINKED(DiscordlinkMessageKey.DISCORD_LINK_ALREADY),

    /** {@code /discordunlink} when the player has no confirmed binding to remove. */
    NOT_LINKED(DiscordlinkMessageKey.DISCORD_LINK_NOT_LINKED);

    private final DiscordlinkMessageKey messageKey;

    DiscordLinkError(DiscordlinkMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the in-game adapter renders for this failure. */
    public DiscordlinkMessageKey messageKey() {
        return messageKey;
    }
}
