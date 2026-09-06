package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import fr.xyness.SCS.ClaimMain;
import fr.xyness.SCS.SimpleClaimSystem;
import fr.xyness.SCS.Types.Claim;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by the SimpleClaimSystem plugin
 * (<a href="https://github.com/Xyness/SimpleClaimSystem">SimpleClaimSystem</a>).
 *
 * <p>SimpleClaimSystem is a {@code compileOnly} soft-depend, so {@code fr.xyness.SCS.*} is absent from the
 * runtime and test classpaths. Every typed SCS reference lives inside a method behind {@link #active()},
 * which only consults the plugin manager. Constructing this provider and asking whether it is active never
 * loads an SCS class. The {@link ClaimMain} handle is resolved lazily on the first {@link #claimAt} call
 * (again past the present guard) and cached, so a server without SCS stays on the no-throw path.
 *
 * <p>The advertised {@code SimpleClaimSystemAPI} is never initialised in 1.13.1, so this binds to the main
 * class's {@link SimpleClaimSystem#getMain()} instead. SCS is chunk-based; a claim is resolved by chunk
 * coordinate with {@link ClaimMain#getClaim(String, int, int)}, a pure in-memory map lookup that never loads
 * a chunk, Folia-safe. Trust is owner-or-member; SCS exposes a real per-claim ban list.
 */
@NullMarked
public final class SimpleClaimSystemClaimProvider implements ClaimProvider {

    private static final String SIMPLE_CLAIM_SYSTEM = "SimpleClaimSystem";

    private final Server server;
    private final Logger log;

    // Resolved lazily on first use, past the plugin-present guard, so the SCS API is never force-loaded when
    // the plugin is absent. Held as Object to keep the field free of an SCS type at class-load time.
    private @Nullable Object claimMain;

    public SimpleClaimSystemClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin scs = server.getPluginManager().getPlugin(SIMPLE_CLAIM_SYSTEM);
        return scs != null && scs.isEnabled();
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
            // A missing or incompatible SCS API surfaces here as NoClassDefFoundError or similar; degrade to
            // "no claim" rather than failing the placement/teleport check.
            log.warn("event=claim_lookup_failed provider=simpleclaimsystem world={} reason={}", world.name(), t);
            return Optional.empty();
        }
    }

    private Optional<ClaimLookup> lookup(WorldRef world, int blockX, int blockZ) {
        ClaimMain main = main();
        if (main == null) {
            return Optional.empty();
        }
        // SCS keys claims by chunk; the block-to-chunk conversion is a pure shift (>> 4) with no chunk read,
        // and getClaim(world, chunkX, chunkZ) is an in-memory map lookup, safe on the region thread / Folia.
        Claim claim = main.getClaim(world.name(), blockX >> 4, blockZ >> 4);
        if (claim == null) {
            return Optional.empty();
        }
        return Optional.of(new SimpleClaimSystemClaimLookup(claim));
    }

    private @Nullable ClaimMain main() {
        ClaimMain cached = (ClaimMain) claimMain;
        if (cached == null) {
            Plugin scs = server.getPluginManager().getPlugin(SIMPLE_CLAIM_SYSTEM);
            if (scs instanceof SimpleClaimSystem instance) {
                cached = instance.getMain();
                claimMain = cached;
            }
        }
        return cached;
    }

    /** Adapts an SCS {@link Claim} to the port's read-only claim view. */
    private record SimpleClaimSystemClaimLookup(Claim claim) implements ClaimLookup {

        @Override
        public boolean isTrusted(UUID player) {
            UUID owner = claim.getUUID();
            if (owner != null && owner.equals(player)) {
                return true;
            }
            return claim.isMember(player);
        }

        @Override
        public boolean isBanned(UUID player) {
            return claim.isBanned(player);
        }

        @Override
        public boolean isOwner(UUID player) {
            return player.equals(claim.getUUID());
        }

        @Override
        public Optional<UUID> owner() {
            return Optional.ofNullable(claim.getUUID());
        }
    }
}
