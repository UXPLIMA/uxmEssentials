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
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.jspecify.annotations.NullMarked;

/**
 * {@link ClaimProvider} backed by GriefPrevention.
 *
 * <p>GriefPrevention is a {@code compileOnly} soft-depend, so {@code me.ryanhamshire.GriefPrevention.*} is
 * absent from the runtime and test classpaths. Every typed GP reference lives inside a method behind
 * {@link #active()}, which only consults the plugin manager. Constructing this provider and asking whether
 * it is active never loads a GP class. The {@code GriefPrevention.instance.dataStore} is read lazily inside
 * {@link #claimAt}, again past the present guard, and any {@link Throwable} degrades to empty/inactive.
 *
 * <p>A claim is resolved with {@code DataStore.getClaimAt(Location, ignoreHeight=true, cachedClaim=null)};
 * trust maps to "is the owner, or holds explicit {@link ClaimPermission#Build}". GriefPrevention has no
 * per-claim ban concept, so {@link ClaimLookup#isBanned} is always {@code false}.
 */
@NullMarked
public final class GriefPreventionClaimProvider implements ClaimProvider {

    private static final String GRIEF_PREVENTION = "GriefPrevention";

    // GriefPrevention resolves claims with ignoreHeight=true, treating them as 2D columns; Y is discarded.
    // Using a constant avoids getHighestBlockYAt(), which is region-bound and unsafe on Folia when the
    // proximity scan reads neighbour columns that may belong to a different region.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;

    public GriefPreventionClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin gp = server.getPluginManager().getPlugin(GRIEF_PREVENTION);
        return gp != null && gp.isEnabled();
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
            // A missing or incompatible GriefPrevention API surfaces here as NoClassDefFoundError or similar;
            // degrade to "no claim" rather than failing the placement/teleport check.
            log.warn("event=claim_lookup_failed provider=griefprevention world={} reason={}", world.name(), t);
            return Optional.empty();
        }
    }

    private Optional<ClaimLookup> lookup(WorldRef world, int blockX, int blockZ) {
        World bukkitWorld = server.getWorld(world.uid());
        if (bukkitWorld == null) {
            return Optional.empty();
        }
        Location location = new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ);
        // ignoreHeight=true treats claims as 2D columns; cachedClaim=null since each lookup is independent.
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) {
            return Optional.empty();
        }
        return Optional.of(new GriefPreventionClaimLookup(claim));
    }

    /** Adapts a GriefPrevention {@link Claim} to the port's read-only claim view. */
    record GriefPreventionClaimLookup(Claim claim) implements ClaimLookup {

        @Override
        public boolean isTrusted(UUID player) {
            UUID owner = claim.getOwnerID();
            if (owner != null && owner.equals(player)) {
                return true;
            }
            return claim.hasExplicitPermission(player, ClaimPermission.Build);
        }

        @Override
        public boolean isBanned(UUID player) {
            // GriefPrevention has no per-claim ban concept.
            return false;
        }

        @Override
        public boolean isOwner(UUID player) {
            // An admin claim has a null owner, so no player owns it; player.equals(null) yields false.
            return player.equals(claim.getOwnerID());
        }

        @Override
        public Optional<UUID> owner() {
            // Empty for an admin claim, which is collectively held and has no single owner UUID.
            return Optional.ofNullable(claim.getOwnerID());
        }
    }
}
