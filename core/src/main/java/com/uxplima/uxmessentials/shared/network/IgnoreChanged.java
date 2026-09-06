package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One player's ignore list changed on the origin backend (an {@code /ignore} or {@code /unignore}), so peers
 * must drop their cached copy of {@code owner}'s ignore set and re-read the authoritative rows on the next
 * ignore-aware {@code /msg} or {@code /mail} delivery. The frame carries the owner identity only; the ignore
 * rows live in the shared database, and a delivery on a peer resolves the list from there. It mirrors
 * {@link HomeChanged} and {@link PlayerWarpChanged}. The ignore list is per-owner, so the unit a peer
 * invalidates is exactly that owner's cached set.
 *
 * @param originServer the backend that made the change
 * @param owner the ignore-list owner whose cached set peers must invalidate
 */
public record IgnoreChanged(String originServer, UUID owner) implements NetworkMessage {

    public IgnoreChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(owner, "owner");
    }

    @Override
    public MessageType type() {
        return MessageType.IGNORE_CHANGED;
    }
}
