package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * A server-wide hologram changed on the origin backend (a {@code /hologram create}/{@code delete}, a move, a
 * line edit, an appearance/visibility/blacklist/page change, …), so peers must drop their cached copy of that
 * hologram and re-render the in-world display so it matches the shared DB. Unlike the per-owner home/warp
 * frames, a hologram is its own per-server display entity on every backend, so dropping the cache is not
 * enough. The peer must reload the named hologram and re-spawn, refresh, or despawn its live display to
 * reflect the change. The frame carries the hologram name only; the durable rows live in the shared database,
 * and the peer re-reads them on receipt.
 *
 * <p>It mirrors {@link WarpChanged} on the wire (a name string), but holograms are keyed by name rather than a
 * single server-wide set, so the name is the unit a peer reloads and re-renders, not just an audit hint.
 *
 * @param originServer the backend that made the change
 * @param name the hologram name that changed; the peer reloads and re-renders exactly this hologram
 */
public record HologramChanged(String originServer, String name) implements NetworkMessage {

    public HologramChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public MessageType type() {
        return MessageType.HOLOGRAM_CHANGED;
    }
}
