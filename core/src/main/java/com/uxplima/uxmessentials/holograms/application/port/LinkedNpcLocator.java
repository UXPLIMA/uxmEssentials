package com.uxplima.uxmessentials.holograms.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port the holograms renderer asks "where is the NPC named {@code npcName}?" without depending on the
 * npc context's internals. The cross-context seam for a hologram linked to an NPC (the
 * link-with-NPC feature). It returns the shared {@link Position} so this contract, like the rest of the holograms
 * context, stays pure: no Bukkit, no npc-domain type.
 *
 * <p>The bukkit adapter realises it over the npc context's in-memory NPC set kept current by the domain-event
 * bus, so a lookup is allocation-light and never touches the database on the render path. An empty result means
 * no NPC stands under that name right now; the renderer then falls back to the hologram's own stored location, so
 * a stale or removed link never crashes a render.
 */
public interface LinkedNpcLocator {

    /** The current position of the NPC named {@code npcName}, or empty when no such NPC exists. */
    Optional<Position> locate(String npcName);
}
