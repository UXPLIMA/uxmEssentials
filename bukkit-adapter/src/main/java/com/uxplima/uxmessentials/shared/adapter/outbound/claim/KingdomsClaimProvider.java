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
 * {@link ClaimProvider} backed by KingdomsX land, reached <b>entirely by reflection</b>: there is no compile
 * dependency on KingdomsX (it is closed source and publishes no Maven coordinate), so this class loads and runs
 * whether or not it is present. The plugin registers under the name {@code Kingdoms}, not {@code KingdomsX}.
 *
 * <p>Here a claim is a claimed land chunk: {@code Land.getLand(Location)} answers {@code null} outside KingdomsX
 * worlds and an unclaimed {@code Land} in the wild, both of which read as "no claim" so unclaimed ground stays
 * free. Land is chunk-keyed, so the lookup uses a constant Y and never asks for a height, keeping it off the
 * region-bound calls that are unsafe on Folia.
 *
 * <p>Ownership in KingdomsX is collective: land belongs to a kingdom rather than to a player, so
 * {@link ClaimLookup#owner()} stays empty exactly as it does for a Towny town, and trust is "your kingdom is the
 * kingdom that holds this land". A player with no kingdom is never trusted. {@link ClaimLookup#isBanned} is
 * always {@code false}: KingdomsX has no per-land ban, only kingdom membership.
 *
 * <p>{@code Land.getKingdom()} is the one step of the chain that cannot be verified against a published source.
 * If a KingdomsX release moves it, the lookup degrades to "claimed, but nobody is trusted here" and logs
 * {@code event=claim_api_partial provider=kingdoms method=getKingdom} once: the strict direction, so a version
 * bump cannot silently open other people's land rather than closing our own.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code org.kingdoms} type. The API chain runs lazily inside {@link #claimAt} past that guard,
 * and any reflective failure logs once and degrades to empty rather than propagating.
 */
@NullMarked
public final class KingdomsClaimProvider implements ClaimProvider {

    private static final String KINGDOMS = "Kingdoms";
    private static final String LAND_CLASS = "org.kingdoms.constants.land.Land";
    private static final String KINGDOM_PLAYER_CLASS = "org.kingdoms.constants.player.KingdomPlayer";

    // Land is chunk-keyed, so a warp is on the same land at any height; a constant Y keeps the lookup off
    // getHighestBlockYAt(), which is region-bound and unsafe on Folia, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();
    private final AtomicBoolean partialApiWarned = new AtomicBoolean();

    public KingdomsClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin kingdoms = server.getPluginManager().getPlugin(KINGDOMS);
        return kingdoms != null && kingdoms.isEnabled();
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
            Object land = landAt(new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ));
            if (land == null || !claimed(land)) {
                return Optional.empty();
            }
            return Optional.of(new KingdomsClaimLookup(new ReflectiveLandView(land)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** {@code Land.getLand(Location)}: the land covering the block, or {@code null} outside a KingdomsX world. */
    private static @Nullable Object landAt(Location location) throws ReflectiveOperationException {
        return Class.forName(LAND_CLASS).getMethod("getLand", Location.class).invoke(null, location);
    }

    private static boolean claimed(Object land) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(land.getClass().getMethod("isClaimed").invoke(land));
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection,
     * and a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=kingdoms reason={}", failure.toString());
        }
    }

    /**
     * One claimed land's kingdom answers, decoupled from reflection so the "same kingdom" trust rule can be
     * exercised without a live KingdomsX. Both sides are opaque identities: whatever a kingdom is compared by.
     */
    interface LandView {

        /** The kingdom holding this land, or empty when it cannot be read. */
        Optional<Object> kingdom();

        /** The kingdom {@code player} belongs to, or empty when they have none or it cannot be read. */
        Optional<Object> kingdomOf(UUID player);
    }

    /** Adapts a claimed KingdomsX land to the port's read-only claim view. */
    record KingdomsClaimLookup(LandView land) implements ClaimLookup {

        KingdomsClaimLookup {
            Objects.requireNonNull(land, "land");
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            Optional<Object> holder = land.kingdom();
            Optional<Object> theirs = land.kingdomOf(player);
            return holder.isPresent() && theirs.isPresent() && holder.get().equals(theirs.get());
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            // KingdomsX has no per-land ban: access is membership only, so nobody is banned from a land as such.
            return false;
        }
    }

    /** Reflective {@link LandView} over a KingdomsX {@code Land}, degrading to empty on any failure. */
    private final class ReflectiveLandView implements LandView {

        private final Object land;

        private ReflectiveLandView(Object land) {
            this.land = land;
        }

        @Override
        public Optional<Object> kingdom() {
            try {
                return identity(land.getClass().getMethod("getKingdom").invoke(land));
            } catch (NoSuchMethodException moved) {
                partialApi();
                return Optional.empty();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        @Override
        public Optional<Object> kingdomOf(UUID player) {
            try {
                Object kingdomPlayer = Class.forName(KINGDOM_PLAYER_CLASS)
                        .getMethod("getKingdomPlayer", UUID.class)
                        .invoke(null, player);
                if (kingdomPlayer == null) {
                    return Optional.empty();
                }
                return identity(kingdomPlayer.getClass().getMethod("getKingdom").invoke(kingdomPlayer));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        /**
         * What a kingdom is compared by: its id when the model exposes one, and otherwise the kingdom object
         * itself. Comparing ids rather than instances survives KingdomsX handing out a fresh wrapper per call.
         */
        private Optional<Object> identity(@Nullable Object kingdom) {
            if (kingdom == null) {
                return Optional.empty();
            }
            try {
                Object id = kingdom.getClass().getMethod("getId").invoke(kingdom);
                return Optional.of(id == null ? kingdom : id);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException noId) {
                return Optional.of(kingdom);
            }
        }

        private void partialApi() {
            if (partialApiWarned.compareAndSet(false, true)) {
                log.warn("event=claim_api_partial provider=kingdoms method=getKingdom");
            }
        }
    }
}
