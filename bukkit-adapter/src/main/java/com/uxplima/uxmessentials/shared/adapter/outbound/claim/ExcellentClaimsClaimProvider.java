package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.lang.reflect.Method;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by ExcellentClaims (nightexpress), reached <b>entirely by reflection</b>
 * there is no compile dependency on the ExcellentClaims API, so this class loads and runs whether or not
 * ExcellentClaims is present.
 *
 * <p>ExcellentClaims is a premium plugin with no public Maven coordinate (its API jar is not published to any
 * anonymous repository), so a typed {@code compileOnly} dependency is impossible. Its API surface is a stable
 * static entry point, {@code su.nightexpress.excellentclaims.ClaimsAPI}, populated on plugin enable, which
 * this provider reaches reflectively.
 *
 * <p>The API chain is {@code ClaimsAPI.getClaimManager()} →
 * {@code ClaimManager.getPrioritizedClaim(org.bukkit.Location)} (covers both chunk-based land claims and
 * region claims, returning {@code null} when unclaimed) → a {@code Claim} exposing {@code getOwnerId()},
 * {@code isOwnerOrMember(UUID)} and {@code isWilderness()}. Trust is owner-or-member. ExcellentClaims has no
 * per-claim ban concept, so {@link ClaimLookup#isBanned} is always {@code false}.
 *
 * <p>{@link #active()} is {@code true} only when the plugin is enabled and {@code ClaimsAPI.isLoaded()} is
 * {@code true}. Reflective handles are resolved on first success and cached; any reflective failure logs once
 * and degrades to inactive/empty: it never propagates.
 */
@NullMarked
public final class ExcellentClaimsClaimProvider implements ClaimProvider {

    private static final String EXCELLENT_CLAIMS = "ExcellentClaims";
    private static final String API_CLASS = "su.nightexpress.excellentclaims.ClaimsAPI";

    // ExcellentClaims resolves claims from a full Location; the homes claim policy treats claims as 2D
    // columns, so a constant Y keeps the lookup off getHighestBlockYAt() and Folia-safe.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;

    private @Nullable Method getClaimManager;
    private @Nullable Method getPrioritizedClaim;
    private @Nullable Method getOwnerId;
    private @Nullable Method isOwnerOrMember;
    private @Nullable Method isWilderness;
    private boolean unavailableLogged;

    public ExcellentClaimsClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin excellent = server.getPluginManager().getPlugin(EXCELLENT_CLAIMS);
        if (excellent == null || !excellent.isEnabled()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            return Boolean.TRUE.equals(apiClass.getMethod("isLoaded").invoke(null));
        } catch (Throwable t) {
            return false;
        }
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
            logUnavailableOnce(t);
            return Optional.empty();
        }
    }

    private Optional<ClaimLookup> lookup(WorldRef world, int blockX, int blockZ) throws Exception {
        World bukkitWorld = server.getWorld(world.uid());
        if (bukkitWorld == null) {
            return Optional.empty();
        }
        resolveHandles();
        Object manager = requireHandle(getClaimManager).invoke(null);
        if (manager == null) {
            return Optional.empty();
        }
        Location location = new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ);
        Object claim = requireHandle(getPrioritizedClaim).invoke(manager, location);
        if (claim == null) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(requireHandle(isWilderness).invoke(claim))) {
            return Optional.empty();
        }
        return Optional.of(new ExcellentClaimsClaimLookup(claim));
    }

    private void resolveHandles() throws Exception {
        if (getPrioritizedClaim != null) {
            return;
        }
        Class<?> apiClass = Class.forName(API_CLASS);
        Class<?> claimClass = Class.forName("su.nightexpress.excellentclaims.api.claim.Claim");
        getClaimManager = apiClass.getMethod("getClaimManager");
        getPrioritizedClaim = getClaimManager.getReturnType().getMethod("getPrioritizedClaim", Location.class);
        getOwnerId = claimClass.getMethod("getOwnerId");
        isOwnerOrMember = claimClass.getMethod("isOwnerOrMember", UUID.class);
        isWilderness = claimClass.getMethod("isWilderness");
    }

    private void logUnavailableOnce(Throwable t) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("event=claim_lookup_failed provider=excellentclaims reason={}", t);
        }
    }

    private static Method requireHandle(@Nullable Method method) {
        return Objects.requireNonNull(method, "reflective method handle not resolved");
    }

    /** Adapts a reflectively-held ExcellentClaims {@code Claim} to the port's read-only claim view. */
    private final class ExcellentClaimsClaimLookup implements ClaimLookup {

        private final Object claim;

        private ExcellentClaimsClaimLookup(Object claim) {
            this.claim = claim;
        }

        @Override
        public boolean isTrusted(UUID player) {
            try {
                Object owner = requireHandle(getOwnerId).invoke(claim);
                if (player.equals(owner)) {
                    return true;
                }
                return Boolean.TRUE.equals(requireHandle(isOwnerOrMember).invoke(claim, player));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public boolean isBanned(UUID player) {
            // ExcellentClaims has no per-claim ban concept: access is owner/member/rank-permission only.
            return false;
        }

        @Override
        public boolean isOwner(UUID player) {
            try {
                return player.equals(requireHandle(getOwnerId).invoke(claim));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public Optional<UUID> owner() {
            try {
                return Optional.ofNullable((UUID) requireHandle(getOwnerId).invoke(claim));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return Optional.empty();
            }
        }
    }
}
