package com.uxplima.uxmessentials.npc.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * An NPC was created, {@code /npc create} under a name no NPC existed at.
 *
 * @param name the name the NPC was created under
 * @param creator the player who created the NPC
 * @param location where the new NPC stands
 */
public record NpcCreated(NpcName name, PlayerRef creator, Position location) implements NpcEvent {

    public NpcCreated {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(location, "location");
    }
}
