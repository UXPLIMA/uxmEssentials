package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A player's mute state changed on the origin backend (a {@code /mute} or {@code /tempmute} wrote a mute row).
 * Unlike a ban, a mute needs no live action on a peer: the messaging context's {@code MutePolicy} re-reads the
 * authoritative mute from the shared DB on the player's next chat attempt, so the new gag takes effect on the
 * very next message without any in-memory state to refresh. The frame is published for symmetry with
 * {@link BanChanged} and so a future peer that does cache mute state has a hook to invalidate it; a peer with
 * no such cache (the current design: the moderation repo is intentionally uncached) treats it as a no-op.
 *
 * @param originServer the backend that issued the mute
 * @param target the muted player
 */
public record MuteChanged(String originServer, UUID target) implements NetworkMessage {

    public MuteChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public MessageType type() {
        return MessageType.MUTE_CHANGED;
    }
}
