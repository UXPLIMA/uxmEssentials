package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * A server-wide warp changed on the origin backend (a {@code /setwarp} or {@code /delwarp}), so peers must
 * drop their cached warp set and re-read the authoritative rows on the next {@code /warp} / {@code /warps}.
 * Warps are server-wide rather than per-player, so there is no owner. The whole cached set is the unit a
 * peer invalidates; the durable warp rows live in the shared database.
 *
 * @param originServer the backend that made the change
 * @param warp the warp name that changed, for audit and logging context (the cache is invalidated wholesale)
 */
public record WarpChanged(String originServer, String warp) implements NetworkMessage {

    public WarpChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(warp, "warp");
    }

    @Override
    public MessageType type() {
        return MessageType.WARP_CHANGED;
    }
}
