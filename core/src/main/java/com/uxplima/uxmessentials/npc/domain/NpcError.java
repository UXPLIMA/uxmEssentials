package com.uxplima.uxmessentials.npc.domain;

import com.uxplima.uxmessentials.npc.application.NpcMessageKey;

/**
 * The modelled failures an NPC operation can produce. Each value carries the {@link NpcMessageKey} the command
 * adapter renders, so a use case returns a {@code Result.err(NpcError.X)} and the caller never re-derives the
 * message: the error carries it, and the failure reason and its localized text never drift apart.
 */
public enum NpcError {

    /** A name no NPC exists under: {@code /npc delete}, {@code movehere}, {@code skin}, {@code command}. */
    NOT_FOUND(NpcMessageKey.NPC_NOT_FOUND),

    /** {@code /npc create} for a name an NPC already exists under. */
    NAME_TAKEN(NpcMessageKey.NPC_NAME_TAKEN),

    /** {@code /npc create} when the owner already holds their {@code uxmessentials.npc.limit.<n>} quota of NPCs. */
    LIMIT_REACHED(NpcMessageKey.NPC_LIMIT_REACHED),

    /** {@code /npc action remove} with a 1-based index outside the NPC's action list. */
    ACTION_INDEX_INVALID(NpcMessageKey.NPC_ACTION_INDEX_INVALID),

    /** {@code /npc skin} against a non-player NPC: only a fake player carries a skin. */
    SKIN_ONLY_PLAYER(NpcMessageKey.NPC_SKIN_ONLY_PLAYER);

    private final NpcMessageKey messageKey;

    NpcError(NpcMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public NpcMessageKey messageKey() {
        return messageKey;
    }
}
