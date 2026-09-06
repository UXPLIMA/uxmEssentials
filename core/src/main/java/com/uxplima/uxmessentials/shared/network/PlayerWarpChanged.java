package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One player's player-warp set changed on the origin backend (a {@code /setpwarp}, {@code /pwarp del}, a
 * visibility flip, or a move/relocate), so peers must drop their cached copy of {@code owner}'s warps and
 * re-read the authoritative rows on the next {@code /pwarp} / {@code /pwarps}. The frame carries the owner
 * identity only; the warp rows live in the shared database, and a {@code /pwarp} on a peer resolves the
 * location from there. It mirrors {@link HomeChanged}. Player-warps are per-owner, so the unit a peer
 * invalidates is exactly that owner's cached set.
 *
 * @param originServer the backend that made the change
 * @param owner the warp owner whose cached player-warp set peers must invalidate
 */
public record PlayerWarpChanged(String originServer, UUID owner) implements NetworkMessage {

    public PlayerWarpChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(owner, "owner");
    }

    @Override
    public MessageType type() {
        return MessageType.PLAYER_WARP_CHANGED;
    }
}
