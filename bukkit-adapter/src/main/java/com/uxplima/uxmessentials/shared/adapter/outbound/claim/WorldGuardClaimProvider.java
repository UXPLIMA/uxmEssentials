package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardReflection;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link ClaimProvider} backed by WorldGuard regions, reached <b>entirely by reflection</b>: there is no
 * compile dependency on WorldGuard, so this class loads and runs whether or not WorldGuard is present. Here a
 * "claim" is a region a player owns or is a member of, so a warp can be gated to land the player has a stake in.
 *
 * <p>The region query runs
 * {@code WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(loc)},
 * where {@code loc} is the Bukkit location adapted through {@code BukkitAdapter.adapt(Location)}; the resulting
 * {@code ApplicableRegionSet.getRegions()} yields the {@code ProtectedRegion}s covering the block. On each region
 * {@code getOwners()} and {@code getMembers()} return a {@code DefaultDomain} whose {@code contains(UUID)} answers
 * membership: ownership is any covering region's owners containing the player, trust is owner-or-member across the
 * covering regions.
 *
 * <p>The {@code __global__} region covers every block in a world and usually has no owner, so a location covered
 * only by it is treated as unclaimed and {@link #claimAt} returns empty. Otherwise no warp could ever be set in
 * unregioned wilderness. A lookup therefore answers over the covering <em>named</em> regions only.
 *
 * <p>{@link ClaimLookup#owner()} stays empty: a region's owners are a domain that may hold several UUIDs, so there
 * is no single canonical owner; callers gate on {@link ClaimLookup#isOwner(UUID)} instead. WorldGuard has no
 * per-region ban concept, so {@link ClaimLookup#isBanned} is always {@code false}.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code com.sk89q} type: a server without WorldGuard loads none of its classes. The region
 * chain runs lazily inside {@link #claimAt} past that guard, and any reflective failure logs once and degrades to
 * empty rather than propagating.
 */
@NullMarked
public final class WorldGuardClaimProvider implements ClaimProvider {

    /** The id of WorldGuard's world-wide region, excluded so unregioned wilderness reads as unclaimed. */
    static final String GLOBAL_REGION = "__global__";

    // Region membership is a 2D question here (a warp is on land the player holds regardless of height) and a
    // constant Y keeps the lookup off getHighestBlockYAt(), which is region-bound and unsafe on Folia when the
    // proximity scan reads neighbour columns owned by a different region.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public WorldGuardClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        return WorldGuardReflection.isEnabled(server);
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
            List<RegionView> named = namedRegions(coveringRegionViews(location));
            return named.isEmpty() ? Optional.empty() : Optional.of(new WorldGuardClaimLookup(named));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** The covering regions minus the world-wide {@code __global__} region, so wilderness reads as unclaimed. */
    static List<RegionView> namedRegions(Collection<? extends RegionView> covering) {
        Objects.requireNonNull(covering, "covering");
        List<RegionView> named = new ArrayList<>(covering.size());
        for (RegionView view : covering) {
            if (!GLOBAL_REGION.equals(view.id())) {
                named.add(view);
            }
        }
        return List.copyOf(named);
    }

    private List<RegionView> coveringRegionViews(Location location) throws ReflectiveOperationException {
        Object applicable = WorldGuardReflection.applicableRegions(
                WorldGuardReflection.createQuery(), WorldGuardReflection.adapt(location));
        if (applicable == null) {
            return List.of();
        }
        Object regions = applicable.getClass().getMethod("getRegions").invoke(applicable);
        if (!(regions instanceof Collection<?> set)) {
            return List.of();
        }
        List<RegionView> views = new ArrayList<>(set.size());
        for (Object region : set) {
            views.add(new ReflectiveRegionView(region, regionId(region)));
        }
        return views;
    }

    private static String regionId(Object region) throws ReflectiveOperationException {
        return String.valueOf(region.getClass().getMethod("getId").invoke(region));
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection, and
     * a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=worldguard reason={}", failure.toString());
        }
    }

    /**
     * One WorldGuard region's owner/member answers, decoupled from reflection so the fold over covering regions
     * and the {@code __global__} exclusion can be exercised without a live WorldGuard.
     */
    interface RegionView {

        String id();

        boolean ownersContains(UUID player);

        boolean membersContains(UUID player);
    }

    /** Reflective {@link RegionView} over a {@code ProtectedRegion}, degrading to {@code false} on any failure. */
    private final class ReflectiveRegionView implements RegionView {

        private final Object region;
        private final String id;

        private ReflectiveRegionView(Object region, String id) {
            this.region = region;
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean ownersContains(UUID player) {
            return domainContains("getOwners", player);
        }

        @Override
        public boolean membersContains(UUID player) {
            return domainContains("getMembers", player);
        }

        private boolean domainContains(String domainAccessor, UUID player) {
            try {
                Object domain = region.getClass().getMethod(domainAccessor).invoke(region);
                if (domain == null) {
                    return false;
                }
                Object contains =
                        domain.getClass().getMethod("contains", UUID.class).invoke(domain, player);
                return Boolean.TRUE.equals(contains);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }
    }

    /** Adapts the covering named WorldGuard regions to the port's read-only claim view. */
    record WorldGuardClaimLookup(List<RegionView> regions) implements ClaimLookup {

        WorldGuardClaimLookup {
            regions = List.copyOf(regions);
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            return regions.stream().anyMatch(r -> r.ownersContains(player) || r.membersContains(player));
        }

        @Override
        public boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            return regions.stream().anyMatch(r -> r.ownersContains(player));
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            // WorldGuard regions have no per-player ban concept: access is expressed through owners/members only.
            return false;
        }
    }
}
