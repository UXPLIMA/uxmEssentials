package com.uxplima.uxmessentials.npc.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * An NPC was removed, {@code /npc delete}. The name is freed so a later {@code /npc create} may reuse it.
 *
 * @param name the name of the removed NPC
 * @param removedBy the player who removed the NPC (audit attribution)
 */
public record NpcDeleted(NpcName name, PlayerRef removedBy) implements NpcEvent {

    public NpcDeleted {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(removedBy, "removedBy");
    }
}
