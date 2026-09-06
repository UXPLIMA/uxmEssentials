package com.uxplima.uxmessentials.holograms.application.port;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * A ranked data source a leaderboard hologram renders: e.g. the top balances, the most playtime. One
 * implementation per source, supplied by the adapter (the holograms context stays free of the source's domain;
 * the impl reaches into the economy/playerstate context and formats each row). The holograms renderer holds a
 * registry of these by id and lays the rows out into lines on the hologram's refresh cadence.
 *
 * <p><b>May block.</b> A provider typically reads a database (the balance baltop query), so the renderer calls
 * {@link #top} off the region thread (through the {@code Scheduler} async port) and applies the result back on the
 * hologram's region thread. An implementation must therefore be safe to call from an async thread and must not
 * touch the Bukkit API itself.
 */
@NullMarked
public interface LeaderboardProvider {

    /** The top {@code limit} ranked rows, highest first; fewer when the source has fewer, empty when it has none. */
    List<LeaderboardEntry> top(int limit);
}
