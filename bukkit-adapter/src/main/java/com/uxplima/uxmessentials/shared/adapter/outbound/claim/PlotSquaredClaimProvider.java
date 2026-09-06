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
 * {@link ClaimProvider} backed by PlotSquared plots, reached <b>entirely by reflection</b>. There is no compile
 * dependency on PlotSquared, so this class loads and runs whether or not PlotSquared is present. Here a "claim"
 * is an owned plot: the block belongs to a claim exactly when PlotSquared reports an owned plot covering it, so a
 * warp can be gated to a plot you own or are added to.
 *
 * <p>The lookup adapts the Bukkit location through {@code BukkitUtil.adapt(Location)} and asks
 * {@code Location.getOwnedPlot()}, which returns {@code null} on an unclaimed plot slot, a road, or a non-plot
 * world; a {@code null} plot reads as unclaimed and {@link #claimAt} returns empty, so unclaimed land stays free.
 * A covering owned plot is mapped to the port's owner/trust model against PlotSquared's owner, added and denied
 * concepts.
 *
 * <p>PlotSquared is UUID-keyed, so ownership maps directly onto the port. Ownership is {@code Plot.isOwner(UUID)},
 * which spans a merged plot's several co-owners, not just the single stored owner. Trust widens ownership to
 * {@code Plot.isAdded(UUID)}, which PlotSquared already defines as owner-or-trusted-or-member, the owner-or-member
 * reading the other providers give trust. Unlike Residence and WorldGuard, PlotSquared exposes a single owner
 * UUID, so {@link ClaimLookup#owner()} returns {@code Plot.getOwner()} rather than staying empty. A ban is the
 * plot's deny list: {@link ClaimLookup#isBanned} defers to {@code Plot.getDenied().contains(UUID)}.
 *
 * <p>The port supplies only a block column with no Y, and a PlotSquared plot spans the whole column, so the
 * lookup builds its Bukkit location at a constant {@code Y}. Any height in the column resolves to the same plot,
 * and the constant keeps the lookup off {@code getHighestBlockYAt}, matching every other provider's 2D-column
 * lookup.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code com.plotsquared} type: a server without PlotSquared loads none of its classes. The
 * PlotSquared API chain runs lazily inside {@link #claimAt} past that guard, and any reflective failure logs once
 * and degrades to empty rather than propagating.
 */
@NullMarked
public final class PlotSquaredClaimProvider implements ClaimProvider {

    private static final String PLOT_SQUARED = "PlotSquared";
    private static final String BUKKIT_UTIL_CLASS = "com.plotsquared.bukkit.util.BukkitUtil";

    // PlotSquared plots span the full world height, a warp is on the plot regardless of height, so a constant Y
    // keeps the lookup off getHighestBlockYAt(), which is region-bound and unsafe on Folia, matching the other
    // providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public PlotSquaredClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin plotSquared = server.getPluginManager().getPlugin(PLOT_SQUARED);
        return plotSquared != null && plotSquared.isEnabled();
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
            Object plot = ownedPlotAt(location);
            if (plot == null) {
                return Optional.empty();
            }
            return Optional.of(new PlotSquaredClaimLookup(new ReflectivePlotView(plot)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** {@code BukkitUtil.adapt(Location).getOwnedPlot()}: the owned plot at the block, or {@code null}. */
    private static @Nullable Object ownedPlotAt(Location location) throws ReflectiveOperationException {
        Object psLocation = Class.forName(BUKKIT_UTIL_CLASS)
                .getMethod("adapt", Location.class)
                .invoke(null, location);
        if (psLocation == null) {
            return null;
        }
        return psLocation.getClass().getMethod("getOwnedPlot").invoke(psLocation);
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection, and
     * a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=plotsquared reason={}", failure.toString());
        }
    }

    /**
     * One PlotSquared plot's owner/added/denied answers, decoupled from reflection so the merged-plot ownership,
     * the owner-or-added trust and the deny-list ban can be exercised without a live PlotSquared.
     */
    interface PlotView {

        /** The plot's owner, or empty when the plot is unowned. */
        Optional<UUID> owner();

        /** Whether {@code player} owns the plot: spanning a merged plot's several co-owners. */
        boolean isOwner(UUID player);

        /** Whether {@code player} is the owner or on the trusted/member list: PlotSquared's owner-or-added check. */
        boolean isAdded(UUID player);

        /** Whether {@code player} is on the plot's deny list. */
        boolean isDenied(UUID player);
    }

    /** Reflective {@link PlotView} over a PlotSquared {@code Plot}, degrading to empty/false on any failure. */
    private final class ReflectivePlotView implements PlotView {

        private final Object plot;

        private ReflectivePlotView(Object plot) {
            this.plot = plot;
        }

        @Override
        public Optional<UUID> owner() {
            try {
                Object owner = plot.getClass().getMethod("getOwner").invoke(plot);
                return owner instanceof UUID id ? Optional.of(id) : Optional.empty();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        @Override
        public boolean isOwner(UUID player) {
            return booleanCall("isOwner", player);
        }

        @Override
        public boolean isAdded(UUID player) {
            return booleanCall("isAdded", player);
        }

        private boolean booleanCall(String method, UUID player) {
            try {
                Object answer = plot.getClass().getMethod(method, UUID.class).invoke(plot, player);
                return Boolean.TRUE.equals(answer);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }

        @Override
        public boolean isDenied(UUID player) {
            try {
                Object denied = plot.getClass().getMethod("getDenied").invoke(plot);
                return denied instanceof Set<?> set && set.contains(player);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }
    }

    /** Adapts a PlotSquared plot to the port's read-only claim view. */
    record PlotSquaredClaimLookup(PlotView plot) implements ClaimLookup {

        PlotSquaredClaimLookup {
            Objects.requireNonNull(plot, "plot");
        }

        @Override
        public boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            return plot.isOwner(player);
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            return plot.isAdded(player);
        }

        @Override
        public Optional<UUID> owner() {
            return plot.owner();
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            return plot.isDenied(player);
        }
    }
}
