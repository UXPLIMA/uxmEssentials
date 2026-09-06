package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the network-shared record of in-flight cross-server teleports. It backs one row per player in
 * the {@code player_warp_pending_teleports} table (PK {@code player_uuid}), so the origin backend records the
 * intent before handing the player across the proxy and the target backend reads it back on join. Every method is
 * a single indexed statement keyed by the player uuid, never a scan.
 */
public interface PendingTeleportStore {

    /**
     * Upsert {@code pending} as the player's single in-flight teleport, replacing any earlier row for them. The
     * origin backend calls this immediately before the proxy connect, so a fresh request always overwrites a stale
     * one rather than accumulating rows.
     */
    void record(PendingTeleport pending);

    /** The player's in-flight teleport, if one is recorded. A single PK read. */
    Optional<PendingTeleport> find(UUID player);

    /** Drop the player's in-flight teleport row; a no-op when none exists. Called once the hop settles or is abandoned. */
    void clear(UUID player);
}
