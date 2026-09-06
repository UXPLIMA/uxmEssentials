package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.Server;
import org.bukkit.World;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ServerMetricsPlaceholders} over the live {@link Server} and the JVM runtime. It reads Bukkit's online
 * roster, slot count, version and per-world population, the Paper {@code getTPS()} window, and the heap figures
 * off {@link Runtime}. Uptime is measured against the enable {@link Instant} bootstrap captured, so it reports
 * how long the plugin has run rather than the whole JVM's age (a reload restarts it).
 *
 * <p>Every read is a cheap accessor with no allocation beyond the per-world lookup, so it is safe on the
 * placeholder path, which a HUD can hit once per refresh per viewer.
 */
@NullMarked
public final class BukkitServerMetrics implements ServerMetricsPlaceholders {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Server server;
    private final Instant enabledAt;
    private final Clock clock;

    public BukkitServerMetrics(Server server, Instant enabledAt) {
        this(server, enabledAt, Clock.systemUTC());
    }

    BukkitServerMetrics(Server server, Instant enabledAt, Clock clock) {
        this.server = Objects.requireNonNull(server, "server");
        this.enabledAt = Objects.requireNonNull(enabledAt, "enabledAt");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public int onlinePlayers() {
        // Read on the PlaceholderAPI expansion thread, not a region tick thread. An onGlobal marshal would be
        // wrong (no region owns this caller) and needless: this reads only the roster size, never a player's
        // mutable entity state, so a torn read during a concurrent join/quit is at worst off by one for one
        // refresh and self-corrects next tick. The same reasoning covers worldPlayers() below.
        return server.getOnlinePlayers().size();
    }

    @Override
    public int maxPlayers() {
        return server.getMaxPlayers();
    }

    @Override
    public String minecraftVersion() {
        return server.getMinecraftVersion();
    }

    @Override
    public Duration uptime() {
        Duration elapsed = Duration.between(enabledAt, clock.instant());
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }

    @Override
    public double[] tps() {
        double[] live = server.getTPS();
        // Defend against an unexpected array length so the resolver can always index 1m/5m/15m safely.
        if (live.length >= 3) {
            return new double[] {live[0], live[1], live[2]};
        }
        double first = live.length > 0 ? live[0] : 20.0;
        return new double[] {first, first, first};
    }

    @Override
    public long ramUsedMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB;
    }

    @Override
    public long ramMaxMb() {
        return Runtime.getRuntime().maxMemory() / BYTES_PER_MB;
    }

    @Override
    public long ramFreeMb() {
        return Runtime.getRuntime().freeMemory() / BYTES_PER_MB;
    }

    @Override
    public String name() {
        return server.getName();
    }

    @Override
    public String motd() {
        return PLAIN.serialize(server.motd());
    }

    @Override
    public int worlds() {
        return server.getWorlds().size();
    }

    @Override
    public OptionalInt worldEntities(String world) {
        @Nullable World found = server.getWorld(Objects.requireNonNull(world, "world"));
        return found == null ? OptionalInt.empty() : OptionalInt.of(found.getEntityCount());
    }

    @Override
    public OptionalInt worldChunks(String world) {
        @Nullable World found = server.getWorld(Objects.requireNonNull(world, "world"));
        return found == null ? OptionalInt.empty() : OptionalInt.of(found.getChunkCount());
    }

    @Override
    public OptionalInt worldPlayers(String world) {
        @Nullable World found = server.getWorld(Objects.requireNonNull(world, "world"));
        return found == null
                ? OptionalInt.empty()
                : OptionalInt.of(found.getPlayers().size());
    }

    @Override
    public Optional<WorldSky> worldSky(String world) {
        @Nullable World found = server.getWorld(Objects.requireNonNull(world, "world"));
        return found == null
                ? Optional.empty()
                : Optional.of(new WorldSky(found.getTime(), found.hasStorm(), found.isThundering()));
    }
}
