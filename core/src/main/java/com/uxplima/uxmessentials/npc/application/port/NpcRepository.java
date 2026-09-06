package com.uxplima.uxmessentials.npc.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;

/**
 * Outbound port for durable, server-wide NPC storage. Every NPC fact (name, world, coordinates, look
 * direction, skin texture/signature, click command, creation time) is a first-class column: there is no
 * opaque JSON blob (the architecture persistence invariant), so an {@link Npc} loaded from its row is rebuilt
 * from queryable fields, and the list query reads them in stored creation order.
 *
 * <p>NPCs are keyed by their {@link NpcName} alone, so a {@code save} upserts on the single-column {@code name}
 * primary key (a move, a re-skin, or a command rebind overwrites the same NPC), and a delete is by name. A
 * cache decorator may sit in front of this port; the contract here is the durable source of truth.
 */
public interface NpcRepository {

    /** The NPC under {@code name}, if one exists. */
    Optional<Npc> find(NpcName name);

    /** Every NPC, in stored creation order, for the {@code /npc list} and spawn-on-enable. */
    List<Npc> all();

    /** True when an NPC already exists under {@code name} (the {@code /npc create} name-taken check). */
    boolean exists(NpcName name);

    /** Insert {@code npc} or overwrite the row under its {@code name} key. */
    void save(Npc npc);

    /** Remove the NPC under {@code name}; a no-op when no such row exists. */
    void delete(NpcName name);
}
