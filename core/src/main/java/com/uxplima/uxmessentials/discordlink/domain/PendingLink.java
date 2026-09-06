package com.uxplima.uxmessentials.discordlink.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A link code a player has generated but not yet redeemed in Discord. There is at most one pending row per
 * player, a fresh {@code /discordlink} replaces the previous one, keyed by the player's account uuid, with
 * the code the unique handle the {@code /link} slash command looks the row up by.
 *
 * <p>The code expires at {@link #expiresAt()}; a redemption after that instant is rejected and the stale row
 * cleared, so a code that leaked but went unused can never be redeemed once its window has passed.
 *
 * @param player the account that generated the code
 * @param code the one-time code presented in Discord
 * @param expiresAt the instant after which the code is no longer valid
 */
public record PendingLink(UUID player, LinkCode code, Instant expiresAt) {

    public PendingLink {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Whether the code's window has closed by {@code now} (the expiry instant itself is treated as expired). */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }
}
