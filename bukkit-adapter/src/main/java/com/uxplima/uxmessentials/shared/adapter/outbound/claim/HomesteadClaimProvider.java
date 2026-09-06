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
 * {@link ClaimProvider} backed by Homestead, reached <b>entirely by reflection</b>. There is no compile
 * dependency on Homestead, so this class loads and runs whether or not Homestead is present.
 *
 * <p>Homestead publishes its API jar only to GitHub Packages, which requires an authenticated token to
 * resolve, so it cannot be verified by the canon check nor pulled anonymously in CI, ruling out a typed
 * {@code compileOnly} dependency. Its API surface is a set of stable static manager classes, which this
 * provider reaches reflectively.
 *
 * <p>Homestead is chunk-based, claiming chunks under a {@code Region}. The lookup is
 * {@code ChunkManager.findChunk(UUID worldId, int chunkX, int chunkZ)}. A pure position-indexed cache read
 * that never loads a chunk (Folia-safe). Returning a {@code RegionChunk} whose {@code getRegion()} yields the
 * owning {@code Region}. Trust is the region owner ({@code Region.getOwnerId()}) or a region member
 * ({@code MemberManager.isMemberOfRegion(long regionId, UUID)}); Homestead has a real per-region ban list
 * ({@code BanManager.isBanned(long regionId, UUID)}).
 *
 * <p>{@link #active()} is {@code true} only when Homestead is enabled and the manager class resolves.
 * Reflective handles are resolved on first success and cached; any reflective failure logs once and degrades
 * to inactive/empty: it never propagates.
 */
@NullMarked
public final class HomesteadClaimProvider implements ClaimProvider {

    private static final String HOMESTEAD = "Homestead";
    private static final String CHUNK_MANAGER_CLASS = "tfagaming.projects.minecraft.homestead.managers.ChunkManager";
    private static final String MEMBER_MANAGER_CLASS = "tfagaming.projects.minecraft.homestead.managers.MemberManager";
    private static final String BAN_MANAGER_CLASS = "tfagaming.projects.minecraft.homestead.managers.BanManager";

    private final Server server;
    private final Logger log;

    private @Nullable Method findChunk;
    private @Nullable Method getRegion;
    private @Nullable Method getRegionUniqueId;
    private @Nullable Method getOwnerId;
    private @Nullable Method isMemberOfRegion;
    private @Nullable Method isBanned;
    private boolean unavailableLogged;

    public HomesteadClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin homestead = server.getPluginManager().getPlugin(HOMESTEAD);
        if (homestead == null || !homestead.isEnabled()) {
            return false;
        }
        try {
            Class.forName(CHUNK_MANAGER_CLASS);
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
        resolveHandles();
        // findChunk(worldId, chunkX, chunkZ) is a position-indexed cache read, no chunk load, Folia-safe.
        Object regionChunk = requireHandle(findChunk).invoke(null, world.uid(), blockX >> 4, blockZ >> 4);
        if (regionChunk == null) {
            return Optional.empty();
        }
        Object region = requireHandle(getRegion).invoke(regionChunk);
        if (region == null) {
            return Optional.empty();
        }
        long regionId = (long) requireHandle(getRegionUniqueId).invoke(region);
        UUID owner = (UUID) requireHandle(getOwnerId).invoke(region);
        return Optional.of(new HomesteadClaimLookup(regionId, owner));
    }

    private void resolveHandles() throws Exception {
        if (findChunk != null) {
            return;
        }
        Class<?> chunkManagerClass = Class.forName(CHUNK_MANAGER_CLASS);
        Class<?> memberManagerClass = Class.forName(MEMBER_MANAGER_CLASS);
        Class<?> banManagerClass = Class.forName(BAN_MANAGER_CLASS);
        Class<?> regionChunkClass = Class.forName("tfagaming.projects.minecraft.homestead.models.RegionChunk");
        Class<?> regionClass = Class.forName("tfagaming.projects.minecraft.homestead.models.Region");
        findChunk = chunkManagerClass.getMethod("findChunk", UUID.class, int.class, int.class);
        getRegion = regionChunkClass.getMethod("getRegion");
        getRegionUniqueId = regionClass.getMethod("getUniqueId");
        getOwnerId = regionClass.getMethod("getOwnerId");
        isMemberOfRegion = memberManagerClass.getMethod("isMemberOfRegion", long.class, UUID.class);
        isBanned = banManagerClass.getMethod("isBanned", long.class, UUID.class);
    }

    private void logUnavailableOnce(Throwable t) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("event=claim_lookup_failed provider=homestead reason={}", t);
        }
    }

    private static Method requireHandle(@Nullable Method method) {
        return Objects.requireNonNull(method, "reflective method handle not resolved");
    }

    /** Adapts a reflectively-resolved Homestead {@code Region} to the port's read-only claim view. */
    private final class HomesteadClaimLookup implements ClaimLookup {

        private final long regionId;
        private final @Nullable UUID ownerId;

        private HomesteadClaimLookup(long regionId, @Nullable UUID ownerId) {
            this.regionId = regionId;
            this.ownerId = ownerId;
        }

        @Override
        public boolean isTrusted(UUID player) {
            if (player.equals(ownerId)) {
                return true;
            }
            try {
                return Boolean.TRUE.equals(requireHandle(isMemberOfRegion).invoke(null, regionId, player));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public boolean isBanned(UUID player) {
            try {
                return Boolean.TRUE.equals(requireHandle(isBanned).invoke(null, regionId, player));
            } catch (Throwable t) {
                logUnavailableOnce(t);
                return false;
            }
        }

        @Override
        public boolean isOwner(UUID player) {
            return player.equals(ownerId);
        }

        @Override
        public Optional<UUID> owner() {
            return Optional.ofNullable(ownerId);
        }
    }
}
