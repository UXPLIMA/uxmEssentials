package com.uxplima.uxmessentials.discordlink.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;

/**
 * The durable store behind account linking: the one-time pending codes and the confirmed
 * UUID&lt;-&gt;Discord bindings. Both must survive a restart and a world rollback (a confirmed link is account
 * identity, never transient), so the implementation is DB-backed, never PDC.
 *
 * <p>There is at most one pending row per player and at most one confirmed row per player; the code is unique
 * across pending rows and the Discord id is unique across confirmed rows. A confirmation binds the player and
 * clears that player's pending row in one write, so a redeemed code is never redeemable twice.
 */
public interface DiscordLinkStore {

    /** Upsert the player's pending code, replacing any previous one: a fresh {@code /discordlink} re-issues. */
    void savePending(PendingLink pending);

    /** The pending row a code belongs to, if one is still outstanding. */
    Optional<PendingLink> findPendingByCode(LinkCode code);

    /** Drop the player's pending row, if any, called on redemption and on an expired-code sweep. */
    void deletePending(UUID player);

    /** Bind the player to the Discord id and clear that player's pending row, atomically. */
    void confirm(ConfirmedLink link);

    /** The confirmed binding for a player, if any. */
    Optional<ConfirmedLink> findByPlayer(UUID player);

    /** The confirmed binding for a Discord id, if any: the seam's already-linked guard reads this. */
    Optional<ConfirmedLink> findByDiscordId(DiscordId discordId);

    /** Remove the player's confirmed binding; {@code true} when a binding existed, {@code false} otherwise. */
    boolean unlink(UUID player);
}
