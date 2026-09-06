package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by XClaim, reached <b>entirely by reflection</b>. There is no compile
 * dependency on XClaim, so this class loads and runs whether or not XClaim is present.
 *
 * <p>XClaim publishes no resolvable Maven coordinate (its JitPack build fails; only the shaded plugin jar is
 * distributed on the Releases tab), so a typed {@code compileOnly} dependency is impossible. Its API surface
 * is a stable static registry, {@code codes.wasabi.xclaim.api.Claim}, which this provider reaches
 * reflectively.
 *
 * <p>XClaim is chunk-based and stores claims in an in-memory registry keyed by {@code ChunkReference}. Rather
 * than {@code Claim.getByChunk(Chunk)} (which would force a chunk load), this provider builds a
 * {@code ChunkReference(World, int chunkX, int chunkZ)} directly and calls {@code Claim.getByChunk(cr)}, a
 * pure in-memory registry scan that never loads a chunk, Folia-safe. Trust is owner (via
 * {@code Claim.getOwner().getUniqueId()}) or an explicit per-player BUILD grant
 * ({@code getUserPermission(OfflinePlayer, Permission.BUILD)}). XClaim has no per-claim ban concept, so
 * {@link ClaimLookup#isBanned} is always {@code false}.
 *
 * <p>{@link #active()} is {@code true} only when XClaim is enabled and the API class resolves. Reflective
 * handles are resolved on first success and cached; any reflective failure logs once and degrades to
 * inactive/empty: it never propagates.
 */
@NullMarked
public final class XClaimClaimProvider implements ClaimProvider {

    private static final String XCLAIM = "XClaim";
    private static final String CLAIM_CLASS = "codes.wasabi.xclaim.api.Claim";
    private static final String CHUNK_REF_CLASS = "codes.wasabi.xclaim.util.ChunkReference";
    private static final String PERMISSION_CLASS = "codes.wasabi.xclaim.api.enums.Permission";

    private final Server server;
    private final Logger log;

    private @Nullable Constructor<?> chunkRefCtor;
    private @Nullable Method getByChunk;
    private @Nullable Method getOwner;
    private @Nullable Method getOwnerUniqueId;
    private @Nullable Method getUserPermission;
    private @Nullable Object buildPermission;
    private boolean unavailableLogged;

    public XClaimClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin xclaim = server.getPluginManager().getPlugin(XCLAIM);
        if (xclaim == null || !xclaim.isEnabled()) {
            return false;
        }
        try {
            Class.forName(CLAIM_CLASS);
            return true;
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
        // Build a ChunkReference from the world and chunk coordinates directly (block >> 4), the registry
        // scan compares references in memory, so no chunk is ever loaded, unlike getByChunk(Chunk).
        Object chunkRef = requireCtor(chunkRefCtor).newInstance(bukkitWorld, blockX >> 4, blockZ >> 4);
        Object claim = requireHandle(getByChunk).invoke(null, chunkRef);
        if (claim == null) {
            return Optional.empty();
        }
        return Optional.of(new XClaimClaimLookup(claim));
    }

    private void resolveHandles() throws Exception {
        if (getByChunk != null) {
            return;
        }
        Class<?> claimClass = Class.forName(CLAIM_CLASS);
        Class<?> chunkRefClass = Class.forName(CHUNK_REF_CLASS);
        Class<?> permissionClass = Class.forName(PERMISSION_CLASS);
        Class<?> xcPlayerClass = Class.forName("codes.wasabi.xclaim.api.XCPlayer");
        chunkRefCtor = chunkRefClass.getConstructor(World.class, int.class, int.class);
        getByChunk = claimClass.getMethod("getByChunk", chunkRefClass);
        getOwner = claimClass.getMethod("getOwner");
        getOwnerUniqueId = xcPlayerClass.getMethod("getUniqueId");
        getUserPermission = claimClass.getMethod("getUserPermission", OfflinePlayer.class, permissionClass);
        buildPermission = permissionClass.getField("BUILD").get(null);
    }

    private void logUnavailableOnce(Throwable t) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("event=claim_lookup_failed provider=xclaim reason={}", t);
        }
    }

    private static Method requireHandle(@Nullable Method method) {
        return Objects.requireNonNull(method, "reflective method handle not resolved");
    }

    private static Constructor<?> requireCtor(@Nullable Constructor<?> ctor) {
        return Objects.requireNonNull(ctor, "reflective constructor handle not resolved");
    }

    /** Adapts a reflectively-held XClaim {@code Claim} to the port's read-only claim view. */
    private final class XClaimClaimLookup implements ClaimLookup {

        private final Object claim;

        private XClaimClaimLookup(Object claim) {
            this.claim = claim;
        }

        @Override
        public boolean isTrusted(UUID player) {
            try {
                Object owner = requireHandle(getOwner).invoke(claim);
                if (owner != null
                        && player.equals(requireHandle(getOwnerUniqueId).invoke(owner))) {
                    return true;
                }
                OfflinePlayer offline = server.getOfflinePlayer(player);
                return Boolean.TRUE.equals(requireHandle(getUserPermission).invoke(claim, offline, buildPermission));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public boolean isBanned(UUID player) {
            // XClaim has no per-claim ban concept: access is a permission/trust deny-model only.
            return false;
        }

        @Override
        public boolean isOwner(UUID player) {
            try {
                Object owner = requireHandle(getOwner).invoke(claim);
                return owner != null
                        && player.equals(requireHandle(getOwnerUniqueId).invoke(owner));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public Optional<UUID> owner() {
            try {
                Object owner = requireHandle(getOwner).invoke(claim);
                if (owner == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(
                        (UUID) requireHandle(getOwnerUniqueId).invoke(owner));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return Optional.empty();
            }
        }
    }
}
