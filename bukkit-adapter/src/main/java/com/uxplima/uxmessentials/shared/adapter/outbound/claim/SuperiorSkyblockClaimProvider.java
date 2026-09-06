package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * {@link ClaimProvider} backed by SuperiorSkyblock2 islands, reached <b>entirely by reflection</b>: there is no
 * compile dependency on SuperiorSkyblock2, so this class loads and runs whether or not it is present. Here a
 * "claim" is an island: the block belongs to a claim exactly when SuperiorSkyblock reports an island covering it,
 * so a warp can be gated to an island you own or are a member of.
 *
 * <p>The lookup is the static {@code SuperiorSkyblockAPI.getIslandAt(Location)}, which is {@code null} off any
 * island or in a non-island world; a {@code null} island reads as unclaimed and {@link #claimAt} returns empty,
 * so land outside any island stays free. A covering island is mapped to the port's owner/trust model against
 * SuperiorSkyblock's owner, member and ban concepts.
 *
 * <p>Ownership is the island's owner ({@code Island.getOwner()}), a {@code SuperiorPlayer} wrapper whose
 * {@code getUniqueId()} gives the real owner UUID; a null owner reads as no owner rather than a match. Trust
 * widens ownership to island membership ({@code Island.isMember(SuperiorPlayer)}), matching the owner-or-member
 * reading the other providers use. A ban is the island's own ban list: {@link ClaimLookup#isBanned} defers to
 * {@code Island.isBanned(SuperiorPlayer)}.
 *
 * <p>SuperiorSkyblock's membership and ban checks take a {@code SuperiorPlayer} wrapper, not a UUID, so the
 * reflective view resolves one through the static {@code SuperiorSkyblockAPI.getPlayer(UUID)} first. That resolver
 * returns {@code null} for a UUID SuperiorSkyblock has never seen; a null wrapper is read as neither a member nor
 * banned, so the island API is never handed a {@code null} player. Ownership needs no wrapper: it compares the
 * queried UUID with the owner's UUID directly.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code com.bgsoftware} type: a server without SuperiorSkyblock2 loads none of its classes. The
 * SuperiorSkyblock API chain runs lazily inside {@link #claimAt} past that guard, and any reflective failure logs
 * once and degrades to empty rather than propagating.
 */
@NullMarked
public final class SuperiorSkyblockClaimProvider implements ClaimProvider {

    private static final String SUPERIOR_SKYBLOCK = "SuperiorSkyblock2";
    private static final String API_CLASS = "com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI";
    private static final String ISLAND_CLASS = "com.bgsoftware.superiorskyblock.api.island.Island";
    private static final String SUPERIOR_PLAYER_CLASS = "com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer";

    // SuperiorSkyblock islands are x/z-bounded areas spanning the full world height. A warp is on an island
    // regardless of height, so a constant Y keeps the lookup off getHighestBlockYAt(), which is region-bound and
    // unsafe on Folia, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public SuperiorSkyblockClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin superiorSkyblock = server.getPluginManager().getPlugin(SUPERIOR_SKYBLOCK);
        return superiorSkyblock != null && superiorSkyblock.isEnabled();
    }

    @Override
    public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(world, "world");
        if (!active()) {
            return Optional.empty();
        }
        World bukkitWorld = server.getWorld(world.uid());
        if (bukkitWorld == null) {
            return Optional.empty();
        }
        try {
            Location location = new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ);
            Object island = islandAt(location);
            if (island == null) {
                return Optional.empty();
            }
            return Optional.of(new SuperiorSkyblockClaimLookup(new ReflectiveIslandView(island)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** {@code SuperiorSkyblockAPI.getIslandAt(Location)}, the island at the block, or {@code null} off any island. */
    private static @Nullable Object islandAt(Location location) throws ReflectiveOperationException {
        return Class.forName(API_CLASS).getMethod("getIslandAt", Location.class).invoke(null, location);
    }

    /** {@code SuperiorSkyblockAPI.getPlayer(UUID)}, the wrapper for the UUID, or {@code null} when unknown. */
    private static @Nullable Object superiorPlayer(UUID player) throws ReflectiveOperationException {
        return Class.forName(API_CLASS).getMethod("getPlayer", UUID.class).invoke(null, player);
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection, and
     * a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=superiorskyblock reason={}", failure.toString());
        }
    }

    /**
     * One SuperiorSkyblock island's owner/member/ban answers, decoupled from reflection so the null-owner
     * handling, the owner-or-member trust fold and the unknown-UUID denial can be exercised without a live
     * SuperiorSkyblock.
     */
    interface IslandView {

        /** The island's owner, or empty for an owner-less island. */
        Optional<UUID> owner();

        /** Whether {@code player} is a member of the island; an unknown UUID (no wrapper) is not a member. */
        boolean isMember(UUID player);

        /** Whether {@code player} is banned from the island; an unknown UUID (no wrapper) is not banned. */
        boolean isBanned(UUID player);
    }

    /** Reflective {@link IslandView} over a SuperiorSkyblock {@code Island}, degrading to empty/false on any failure. */
    private final class ReflectiveIslandView implements IslandView {

        private final Object island;

        private ReflectiveIslandView(Object island) {
            this.island = island;
        }

        @Override
        public Optional<UUID> owner() {
            try {
                Object owner = Class.forName(ISLAND_CLASS).getMethod("getOwner").invoke(island);
                if (owner == null) {
                    return Optional.empty();
                }
                Object id = Class.forName(SUPERIOR_PLAYER_CLASS)
                        .getMethod("getUniqueId")
                        .invoke(owner);
                return id instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        @Override
        public boolean isMember(UUID player) {
            return islandCheck("isMember", player);
        }

        @Override
        public boolean isBanned(UUID player) {
            return islandCheck("isBanned", player);
        }

        /**
         * Resolve the {@code SuperiorPlayer} wrapper and defer to {@code Island.<method>(SuperiorPlayer)}. A UUID
         * SuperiorSkyblock has no wrapper for reads as {@code false}. The guarded null keeps the island API from
         * being handed a {@code null} player.
         */
        private boolean islandCheck(String method, UUID player) {
            try {
                Object wrapper = superiorPlayer(player);
                if (wrapper == null) {
                    return false;
                }
                Class<?> superiorPlayerClass = Class.forName(SUPERIOR_PLAYER_CLASS);
                Object answer = Class.forName(ISLAND_CLASS)
                        .getMethod(method, superiorPlayerClass)
                        .invoke(island, wrapper);
                return Boolean.TRUE.equals(answer);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }
    }

    /** Adapts a SuperiorSkyblock island to the port's read-only claim view. */
    record SuperiorSkyblockClaimLookup(IslandView island) implements ClaimLookup {

        SuperiorSkyblockClaimLookup {
            Objects.requireNonNull(island, "island");
        }

        @Override
        public boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            // A null owner (owner-less island) reads as no match, never as ownership.
            return island.owner().map(player::equals).orElse(false);
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            return isOwner(player) || island.isMember(player);
        }

        @Override
        public Optional<UUID> owner() {
            return island.owner();
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            return island.isBanned(player);
        }
    }
}
