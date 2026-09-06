package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

/**
 * Read seam the expansion queries for the {@code worlds_*} placeholders. It is an adapter over the worlds
 * context's {@code WorldRepository} (the registry of managed worlds) and {@code WorldEngine} (the live
 * world handles), wired during bootstrap; when the worlds module is disabled the seam is absent and the
 * placeholders degrade to the dash.
 *
 * <p>Every read is server-wide and player-agnostic. The managed/loaded counts and the default world are the
 * same for every requester, but each is still resolved per placeholder request, off the cached repository
 * and the live world handles so the read is cheap on the placeholder path.
 */
public interface WorldsPlaceholders {

    /** How many worlds the plugin manages (the registry size), loaded or not. */
    int managedCount();

    /** How many worlds are currently loaded server-wide. */
    int loadedCount();

    /** The server's default (primary) world name, or empty when no world is loaded. */
    Optional<String> defaultWorld();

    /** How many players are in the default world, or zero when there is no default world. */
    int defaultWorldPlayers();
}
