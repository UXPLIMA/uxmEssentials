package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import net.weesli.rclaim.api.RClaimProvider;
import net.weesli.rclaim.api.model.Claim;
import org.jspecify.annotations.NullMarked;

/**
 * {@link ClaimProvider} backed by the RClaim plugin (<a href="https://github.com/Weesli/RClaim">RClaim</a>).
 *
 * <p>RClaim is a {@code compileOnly} soft-depend, so {@code net.weesli.rclaim.*} is absent from the runtime
 * and test classpaths. Every typed RClaim reference lives inside a method behind {@link #active()}, which
 * only consults the plugin manager. Constructing this provider and asking whether it is active never loads
 * an RClaim class. The static {@link RClaimProvider} accessors are read lazily inside {@link #claimAt}, again
 * past the present guard, and any {@link Throwable} degrades to empty/inactive.
 *
 * <p>RClaim is chunk-based and stores claim ownership in the chunk's {@code PersistentDataContainer}, so its
 * {@code ClaimManager.getClaim(Location)} would force a chunk load (region-bound, unsafe on Folia). This
 * provider instead matches against the in-memory claim cache by world name and chunk-corner coordinates
 * a pure-datastore lookup that never touches a chunk. Trust is owner-or-member; RClaim has no per-claim ban
 * concept, so {@link ClaimLookup#isBanned} is always {@code false}.
 */
@NullMarked
public final class RClaimClaimProvider implements ClaimProvider {

    private static final String RCLAIM = "RClaim";

    private final Server server;
    private final Logger log;

    public RClaimClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin rclaim = server.getPluginManager().getPlugin(RCLAIM);
        return rclaim != null && rclaim.isEnabled();
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
            // A missing or incompatible RClaim API surfaces here as NoClassDefFoundError or similar; degrade
            // to "no claim" rather than failing the placement/teleport check.
            log.warn("event=claim_lookup_failed provider=rclaim world={} reason={}", world.name(), t);
            return Optional.empty();
        }
    }

    private Optional<ClaimLookup> lookup(WorldRef world, int blockX, int blockZ) {
        if (RClaimProvider.getCacheManager() == null) {
            return Optional.empty();
        }
        // RClaim stores a claim's chunk via its block-corner coordinates (chunkX << 4). Convert the queried
        // block to its chunk corner and match against the cached claims by world name, a pure in-memory scan
        // that never loads a chunk, unlike getClaim(Location) which reads the chunk's PersistentDataContainer.
        int cornerX = (blockX >> 4) << 4;
        int cornerZ = (blockZ >> 4) << 4;
        for (Claim claim :
                RClaimProvider.getCacheManager().getClaims().getCache().values()) {
            if (claim.getX() == cornerX
                    && claim.getZ() == cornerZ
                    && world.name().equals(claim.getWorldName())) {
                return Optional.of(new RClaimClaimLookup(claim));
            }
        }
        return Optional.empty();
    }

    /** Adapts an RClaim {@link Claim} to the port's read-only claim view. */
    private record RClaimClaimLookup(Claim claim) implements ClaimLookup {

        @Override
        public boolean isTrusted(UUID player) {
            return claim.isOwner(player) || claim.isMember(player);
        }

        @Override
        public boolean isBanned(UUID player) {
            // RClaim has no per-claim ban concept: access is modelled as membership/permission only.
            return false;
        }

        @Override
        public boolean isOwner(UUID player) {
            return claim.isOwner(player);
        }

        @Override
        public Optional<UUID> owner() {
            return Optional.ofNullable(claim.getOwner());
        }
    }
}
