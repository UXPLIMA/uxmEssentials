package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.domain.Npc;

/**
 * A domain {@link Npc} paired with the two stable protocol identities its packets need: the fake player's
 * profile uuid and its entity id. Both are derived once per NPC and reused across re-renders so a viewer's
 * client keeps linking the skin to the same entry and never sees two copies.
 *
 * <p>The profile uuid is derived deterministically from the NPC name ({@code nameUUIDFromBytes("uxmnpc:" +
 * name)}), so it is stable across a restart, identical on every viewer, and links the {@code textures} property
 * carried on the tab-add to the spawned entity. The entity id is allocated from the shared server counter when
 * the NPC is first tracked and kept for the NPC's whole life: a move or a re-skin reuses it (the explicit-render
 * path removes then re-spawns the fake player under the same id), and only a delete frees it, a later recreate
 * allocates a fresh one. The idempotent refresh tick never reallocates either; it only spawns/removes on a
 * range transition.
 *
 * @param npc the current stored snapshot this fake player renders
 * @param entityId the protocol entity id the spawn/remove packets carry
 */
record RenderedNpc(Npc npc, int entityId) {

    private static final String PROFILE_SEED_PREFIX = "uxmnpc:";

    RenderedNpc {
        Objects.requireNonNull(npc, "npc");
    }

    /** A copy carrying a newer {@code Npc} snapshot under the same entity id (a move or a re-skin). */
    RenderedNpc withNpc(Npc updated) {
        return new RenderedNpc(updated, entityId);
    }

    /** The deterministic profile uuid for this NPC, derived from its name so the skin always links. */
    UUID profileId() {
        return profileIdFor(npc.name().value());
    }

    /** The deterministic profile uuid for an NPC name: exposed so tests can assert stability. */
    static UUID profileIdFor(String name) {
        return UUID.nameUUIDFromBytes((PROFILE_SEED_PREFIX + name).getBytes(StandardCharsets.UTF_8));
    }
}
