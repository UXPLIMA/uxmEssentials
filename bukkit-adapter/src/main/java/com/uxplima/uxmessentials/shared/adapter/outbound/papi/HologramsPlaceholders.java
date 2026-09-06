package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

/**
 * Read seam the expansion queries for the {@code holograms_*} placeholders. It is an adapter over the holograms
 * context's {@code HologramRepository} wired during bootstrap; when the holograms module is disabled the seam is
 * absent and the placeholder degrades to the dash.
 *
 * <p>The only read is server-wide: how many holograms are registered. It is player-agnostic. The value is the
 * same for every requester, but it is still resolved per placeholder request, off the cached repository so the
 * read is cheap on the placeholder path.
 */
public interface HologramsPlaceholders {

    /** How many holograms are currently registered server-wide. */
    int count();
}
