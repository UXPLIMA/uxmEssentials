package com.uxplima.uxmessentials.npc.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * An NPC was re-anchored, {@code /npc movehere} or {@code /npc moveto}. Carries the NPC's new position so a
 * subscriber re-anchors anything pinned to the NPC (a hologram linked to it) without re-reading the repository.
 *
 * @param name the name of the moved NPC
 * @param to where the NPC now stands
 */
public record NpcMoved(NpcName name, Position to) implements NpcEvent {

    public NpcMoved {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(to, "to");
    }
}
