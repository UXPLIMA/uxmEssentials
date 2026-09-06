package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code discordlink_*} placeholders. It is an adapter over the
 * discord-link context's DB-backed {@code DiscordLinkStore} wired during bootstrap; when the discordlink module
 * is disabled the seam is absent and the placeholders degrade to the dash.
 *
 * <p>The binding is keyed by the player's UUID and lives in the host persistence, not the optional Discord
 * bridge jar, so both reads answer for an offline player too. The stored binding carries only the Discord
 * snowflake id (account identity), no Discord username is persisted, so there is no username placeholder.
 */
public interface DiscordlinkPlaceholders {

    /** Whether {@code who}'s account is currently bound to a Discord user. */
    boolean linked(PlayerRef who);

    /** The bound Discord snowflake id for {@code who}, or empty when the account is not linked. */
    Optional<String> discordId(PlayerRef who);
}
