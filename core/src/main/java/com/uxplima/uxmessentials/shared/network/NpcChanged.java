package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * A server-wide NPC changed on the origin backend (a {@code /npc create}/{@code delete}, a move, a re-skin, an
 * equipment/glow/pose/scale/look/type/action edit, …), so peers must reload their cached copy of that NPC and
 * re-render the in-world fake player so it matches the shared DB. Like {@link HologramChanged}, an NPC is its
 * own per-server packet entity on every backend, so dropping the cache is not enough. The peer must reload the
 * named NPC and re-spawn, refresh, or despawn its live fake player to reflect the change. The frame carries the
 * NPC name only; the durable rows live in the shared database, and the peer re-reads them on receipt.
 *
 * <p>It mirrors {@link HologramChanged} on the wire (an origin string and a name string): NPCs are keyed by
 * name, so the name is the unit a peer reloads and re-renders.
 *
 * @param originServer the backend that made the change
 * @param name the NPC name that changed; the peer reloads and re-renders exactly this NPC
 */
public record NpcChanged(String originServer, String name) implements NetworkMessage {

    public NpcChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public MessageType type() {
        return MessageType.NPC_CHANGED;
    }
}
