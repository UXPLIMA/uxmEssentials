package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A player's ban state changed on the origin backend (a {@code /ban} or {@code /tempban} wrote a tempban row),
 * so a peer that has the same player connected should re-check the now-authoritative ban and kick them if the
 * ban is in effect. The durable enforcement is free already. The shared database holds the ban and every
 * backend re-reads it on the next login, so this frame adds only the <em>live</em> effect: the player a peer
 * already has online does not linger until their next reconnect.
 *
 * <p>The frame carries only the target's UUID; the ban itself (reason, expiry) is read from the shared DB on
 * the peer, never carried on the wire, the same notification-not-source-of-truth rule as every other frame.
 *
 * @param originServer the backend that issued the ban
 * @param target the banned player; a peer that has them online re-evaluates and kicks
 */
public record BanChanged(String originServer, UUID target) implements NetworkMessage {

    public BanChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public MessageType type() {
        return MessageType.BAN_CHANGED;
    }
}
