package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by GriefDefender, reached <b>entirely by reflection</b>. There is no compile
 * dependency on the GriefDefender API, so this class loads and runs whether or not GriefDefender is present.
 *
 * <p>The only Maven coordinate GriefDefender publishes is a JitPack build pinned to a mutable commit hash
 * (the project ships no version tags), which JitPack may garbage-collect. Too fragile for a pinned
 * {@code compileOnly} dependency. The public API surface ({@code com.griefdefender.api.GriefDefender}) is a
 * stable static entry point, so this provider reaches it reflectively instead.
 *
 * <p>The API chain is {@code GriefDefender.getCore()} →
 * {@code Core.getClaimAt(UUID worldUniqueId, int x, int y, int z)} (returns {@code null} when unclaimed) → a
 * {@code Claim} exposing {@code getOwnerUniqueId()}, {@code isUserTrusted(UUID, TrustType)} and
 * {@code isWilderness()}. Trust uses the builder-level {@code TrustTypes.BUILDER} constant (the type argument
 * is the <i>minimum</i> required, so managers also pass). GriefDefender has no per-claim ban concept, so
 * {@link ClaimLookup#isBanned} is always {@code false}.
 *
 * <p>{@link #active()} is {@code true} only when GriefDefender is enabled and the core resolves. Reflective
 * handles are resolved on first success and cached; any reflective failure logs once and degrades to
 * inactive/empty: it never propagates.
 */
@NullMarked
public final class GriefDefenderClaimProvider implements ClaimProvider {

    private static final String GRIEF_DEFENDER = "GriefDefender";
    private static final String API_CLASS = "com.griefdefender.api.GriefDefender";
    private static final String TRUST_TYPES_CLASS = "com.griefdefender.api.claim.TrustTypes";

    // GriefDefender supports 3D cuboid claims, but the homes claim policy treats claims as 2D columns; a
    // constant Y keeps the lookup off getHighestBlockYAt() and Folia-safe, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;

    private @Nullable Method getCore;
    private @Nullable Method getClaimAt;
    private @Nullable Object builderTrustType;
    private @Nullable Method getOwnerUniqueId;
    private @Nullable Method isUserTrusted;
    private @Nullable Method isWilderness;
    private boolean unavailableLogged;

    public GriefDefenderClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin gd = server.getPluginManager().getPlugin(GRIEF_DEFENDER);
        if (gd == null || !gd.isEnabled()) {
            return false;
        }
        try {
            return core() != null;
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
        resolveHandles();
        Object core = core();
        if (core == null) {
            return Optional.empty();
        }
        Object claim = requireHandle(getClaimAt).invoke(core, world.uid(), blockX, CLAIM_LOOKUP_Y, blockZ);
        if (claim == null) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(requireHandle(isWilderness).invoke(claim))) {
            return Optional.empty();
        }
        return Optional.of(new GriefDefenderClaimLookup(claim));
    }

    private @Nullable Object core() throws Exception {
        Class<?> apiClass = Class.forName(API_CLASS);
        Method accessor = getCore;
        if (accessor == null) {
            accessor = apiClass.getMethod("getCore");
            getCore = accessor;
        }
        return accessor.invoke(null);
    }

    private void resolveHandles() throws Exception {
        if (getClaimAt != null) {
            return;
        }
        Class<?> coreClass = Class.forName("com.griefdefender.api.Core");
        Class<?> claimClass = Class.forName("com.griefdefender.api.claim.Claim");
        Class<?> trustTypeClass = Class.forName("com.griefdefender.api.claim.TrustType");
        getClaimAt = coreClass.getMethod("getClaimAt", UUID.class, int.class, int.class, int.class);
        getOwnerUniqueId = claimClass.getMethod("getOwnerUniqueId");
        isUserTrusted = claimClass.getMethod("isUserTrusted", UUID.class, trustTypeClass);
        isWilderness = claimClass.getMethod("isWilderness");
        builderTrustType = Class.forName(TRUST_TYPES_CLASS).getField("BUILDER").get(null);
    }

    private void logUnavailableOnce(Throwable t) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("event=claim_lookup_failed provider=griefdefender reason={}", t);
        }
    }

    private static Method requireHandle(@Nullable Method method) {
        return Objects.requireNonNull(method, "reflective method handle not resolved");
    }

    /** Adapts a reflectively-held GriefDefender {@code Claim} to the port's read-only claim view. */
    private final class GriefDefenderClaimLookup implements ClaimLookup {

        private final Object claim;

        private GriefDefenderClaimLookup(Object claim) {
            this.claim = claim;
        }

        @Override
        public boolean isTrusted(UUID player) {
            try {
                Object owner = requireHandle(getOwnerUniqueId).invoke(claim);
                if (player.equals(owner)) {
                    return true;
                }
                return Boolean.TRUE.equals(requireHandle(isUserTrusted).invoke(claim, player, builderTrustType));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public boolean isBanned(UUID player) {
            // GriefDefender has no per-claim ban concept: access is expressed through trust and flags only.
            return false;
        }

        @Override
        public boolean isOwner(UUID player) {
            try {
                return player.equals(requireHandle(getOwnerUniqueId).invoke(claim));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public Optional<UUID> owner() {
            try {
                return Optional.ofNullable(
                        (UUID) requireHandle(getOwnerUniqueId).invoke(claim));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return Optional.empty();
            }
        }
    }
}
