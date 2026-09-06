package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * {@link ClaimProvider} backed by BentoBox islands, reached <b>entirely by reflection</b>. There is no compile
 * dependency on BentoBox, so this class loads and runs whether or not BentoBox is present. Here a "claim" is an
 * island: the block belongs to a claim exactly when BentoBox reports an island covering it, so a warp can be
 * gated to an island you own or are a member of.
 *
 * <p>The lookup is {@code BentoBox.getInstance().getIslands().getIslandAt(Location)}, which is empty in
 * unclaimed island space or a non-island world; an empty result reads as unclaimed and {@link #claimAt} returns
 * empty, so land outside any island stays free. A covering island is mapped to the port's owner/trust model
 * against BentoBox's owner, member and ban concepts.
 *
 * <p>Ownership is the island's owner ({@code Island.getOwner()}), which is empty for an unowned or spawn island
 *: a null owner reads as no owner rather than a match. Trust widens ownership to island membership
 * ({@code Island.getMemberSet()}), matching the owner-or-member reading the other providers use. A ban is the
 * island's own ban list: {@link ClaimLookup#isBanned} defers to {@code Island.isBanned(UUID)}.
 *
 * <p>BentoBox is the framework; game modes (BSkyBlock, AcidIsland, …) run on top of it. The single
 * {@code BentoBox} plugin-present check covers every one of them, because {@code getIslandAt} spans all island
 * worlds, so there is no need to probe individual game-mode plugins.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code world.bentobox} type: a server without BentoBox loads none of its classes. The
 * BentoBox API chain runs lazily inside {@link #claimAt} past that guard, and any reflective failure logs once
 * and degrades to empty rather than propagating.
 */
@NullMarked
public final class BentoBoxClaimProvider implements ClaimProvider {

    private static final String BENTO_BOX = "BentoBox";
    private static final String BENTO_BOX_CLASS = "world.bentobox.bentobox.BentoBox";

    // BentoBox islands are x/z-bounded areas spanning the full world height. A warp is on an island regardless
    // of height, so a constant Y keeps the lookup off getHighestBlockYAt(), which is region-bound and unsafe on
    // Folia, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public BentoBoxClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin bentoBox = server.getPluginManager().getPlugin(BENTO_BOX);
        return bentoBox != null && bentoBox.isEnabled();
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
            return Optional.of(new BentoBoxClaimLookup(new ReflectiveIslandView(island)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** {@code BentoBox.getInstance().getIslands().getIslandAt(Location)} unwrapped, or {@code null} off any island. */
    private static @Nullable Object islandAt(Location location) throws ReflectiveOperationException {
        Object instance =
                Class.forName(BENTO_BOX_CLASS).getMethod("getInstance").invoke(null);
        if (instance == null) {
            return null;
        }
        Object islands = instance.getClass().getMethod("getIslands").invoke(instance);
        Object result =
                islands.getClass().getMethod("getIslandAt", Location.class).invoke(islands, location);
        return result instanceof Optional<?> island ? island.orElse(null) : null;
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection, and
     * a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=bentobox reason={}", failure.toString());
        }
    }

    /**
     * One BentoBox island's owner/member/ban answers, decoupled from reflection so the null-owner handling and
     * the owner-or-member trust fold can be exercised without a live BentoBox.
     */
    interface IslandView {

        /** The island's owner, or empty for an unowned or spawn island. */
        Optional<UUID> owner();

        /** Whether {@code player} is a member of the island (owner included in BentoBox's own member set). */
        boolean isMember(UUID player);

        /** Whether {@code player} is banned from the island. */
        boolean isBanned(UUID player);
    }

    /** Reflective {@link IslandView} over a BentoBox {@code Island}, degrading to empty/false on any failure. */
    private final class ReflectiveIslandView implements IslandView {

        private final Object island;

        private ReflectiveIslandView(Object island) {
            this.island = island;
        }

        @Override
        public Optional<UUID> owner() {
            try {
                Object owner = island.getClass().getMethod("getOwner").invoke(island);
                return owner instanceof UUID id ? Optional.of(id) : Optional.empty();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        @Override
        public boolean isMember(UUID player) {
            try {
                Object members = island.getClass().getMethod("getMemberSet").invoke(island);
                return members instanceof Set<?> set && set.contains(player);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }

        @Override
        public boolean isBanned(UUID player) {
            try {
                Object banned =
                        island.getClass().getMethod("isBanned", UUID.class).invoke(island, player);
                return Boolean.TRUE.equals(banned);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }
    }

    /** Adapts a BentoBox island to the port's read-only claim view. */
    record BentoBoxClaimLookup(IslandView island) implements ClaimLookup {

        BentoBoxClaimLookup {
            Objects.requireNonNull(island, "island");
        }

        @Override
        public boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            // A null owner (unowned or spawn island) reads as no match, never as ownership.
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
