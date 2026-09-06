package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Area;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by the Lands plugin (<a href="https://www.spigotmc.org/resources/53313/">Lands</a>).
 *
 * <p>Lands is a {@code compileOnly} soft-depend, so {@code me.angeschossen.lands.api.*} is absent from the
 * runtime and test classpaths. Every typed Lands reference lives inside a method behind {@link #active()},
 * which only consults the plugin manager. Constructing this provider and asking whether it is active never
 * loads a Lands class. The {@link LandsIntegration} handle is resolved lazily on the first {@link #claimAt}
 * call (again past the present guard) and cached, so a server without Lands stays on the no-throw path.
 *
 * <p>A claim is resolved with {@link LandsIntegration#getArea(Location)} at the target column; the returned
 * {@link Area} exposes {@link Area#isTrusted(UUID)} (owner-or-trusted) and {@link Area#isBanned(UUID)}
 * directly, which map straight onto the {@link ClaimLookup} predicates. Any {@link Throwable} from a missing
 * or incompatible API degrades to empty/inactive rather than propagating.
 */
@NullMarked
public final class LandsClaimProvider implements ClaimProvider {

    private static final String LANDS = "Lands";

    // Lands resolves claims by 2D column; the Y coordinate is irrelevant to getArea(). Using a constant
    // avoids getHighestBlockYAt(), which is a region-bound chunk read and would be unsafe on Folia when
    // the proximity scan visits neighbour columns that may belong to a different region.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Plugin plugin;
    private final Server server;
    private final Logger log;

    // Resolved lazily on first use, past the plugin-present guard, so the Lands API is never force-loaded
    // when the plugin is absent. Held as Object to keep the field free of a Lands type at class-load time.
    private @Nullable Object integration;

    public LandsClaimProvider(Plugin plugin, Server server, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin lands = server.getPluginManager().getPlugin(LANDS);
        return lands != null && lands.isEnabled();
    }

    @Override
    public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(world, "world");
        if (!active()) {
            return Optional.empty();
        }
        try {
            return lookup(world, blockX, blockZ);
        } catch (Throwable t) {
            // A missing or incompatible Lands API surfaces here as NoClassDefFoundError or similar; degrade
            // to "no claim" rather than failing the placement/teleport check.
            log.warn("event=claim_lookup_failed provider=lands world={} reason={}", world.name(), t);
            return Optional.empty();
        }
    }

    private Optional<ClaimLookup> lookup(WorldRef world, int blockX, int blockZ) {
        World bukkitWorld = server.getWorld(world.uid());
        if (bukkitWorld == null) {
            return Optional.empty();
        }
        Location location = new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ);
        Area area = integration().getArea(location);
        if (area == null) {
            return Optional.empty();
        }
        return Optional.of(new LandsClaimLookup(area));
    }

    private LandsIntegration integration() {
        LandsIntegration cached = (LandsIntegration) integration;
        if (cached == null) {
            cached = LandsIntegration.of(plugin);
            integration = cached;
        }
        return cached;
    }

    /** Adapts a Lands {@link Area} to the port's read-only claim view. */
    record LandsClaimLookup(Area area) implements ClaimLookup {

        @Override
        public boolean isTrusted(UUID player) {
            return area.isTrusted(player);
        }

        @Override
        public boolean isBanned(UUID player) {
            return area.isBanned(player);
        }

        @Override
        public boolean isOwner(UUID player) {
            return player.equals(area.getOwnerUID());
        }

        @Override
        public Optional<UUID> owner() {
            return Optional.ofNullable(area.getOwnerUID());
        }
    }
}
