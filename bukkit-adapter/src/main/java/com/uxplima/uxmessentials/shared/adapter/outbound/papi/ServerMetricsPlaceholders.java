package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Read seam the expansion queries for the {@code server_*} placeholders. The server-wide metrics that need no
 * requesting player (online count, slots, version, process uptime, TPS, heap usage, per-world population). Unlike
 * every other seam this one is not owned by a feature context: it reads Bukkit and JVM globals, so it is always
 * present, wired once in bootstrap regardless of which modules are enabled.
 *
 * <p>It exposes the raw primitives only; the {@link PlaceholderResolver} owns the rendering (the dash defaults,
 * the megabyte rounding, the green/yellow/red TPS colouring). Keeping the seam value-typed lets the resolver test
 * exercise the {@code server_*} family with a plain fake, no live server.
 */
public interface ServerMetricsPlaceholders {

    /** Players currently connected. */
    int onlinePlayers();

    /** The configured slot count. */
    int maxPlayers();

    /** The running Minecraft version, e.g. {@code 1.21.11}. */
    String minecraftVersion();

    /** How long the plugin has been enabled, captured from the enable timestamp, not JVM start. */
    Duration uptime();

    /**
     * The 1-minute, 5-minute and 15-minute tick rates, in that order. Always three elements; a server that has
     * not yet measured a window reports its idle ceiling for it.
     */
    double[] tps();

    /** Used heap, in megabytes (committed total minus free). */
    long ramUsedMb();

    /** Maximum heap the JVM may grow to, in megabytes. */
    long ramMaxMb();

    /** Free heap within the currently committed total, in megabytes. */
    long ramFreeMb();

    /** The server's own name, as the operator set it. */
    String name();

    /** The message of the day, already flattened to plain text. */
    String motd();

    /** How many worlds are loaded. */
    int worlds();

    /** Entities in the named world, or empty when no world carries that name. */
    OptionalInt worldEntities(String world);

    /** Loaded chunks in the named world, or empty when no world carries that name. */
    OptionalInt worldChunks(String world);

    /** Players in the named world, or empty when no world carries that name. */
    OptionalInt worldPlayers(String world);

    /** The named world's time of day and sky, or empty when no world carries that name. */
    Optional<WorldSky> worldSky(String world);

    /**
     * A world's clock and weather, read together because a HUD line that shows one usually shows the other.
     *
     * @param time the time of day, in ticks
     * @param storming whether it is raining there
     * @param thundering whether a thunderstorm is overhead
     */
    record WorldSky(long time, boolean storming, boolean thundering) {}
}
