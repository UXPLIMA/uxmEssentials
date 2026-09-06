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
 * {@link ClaimProvider} backed by Towny town plots, reached <b>entirely by reflection</b>. There is no compile
 * dependency on Towny, so this class loads and runs whether or not Towny is present. Here a "claim" is a town
 * plot: the block belongs to a claim exactly when Towny reports a {@code TownBlock} covering it, so a warp can
 * be gated to a plot you own or a town you belong to.
 *
 * <p>The lookup is {@code TownyAPI.getInstance().getTownBlock(Location)}, which returns {@code null} in the
 * wilderness or a non-Towny world; a {@code null} plot reads as unclaimed and {@link #claimAt} returns empty, so
 * unclaimed land stays free. A covering plot is mapped to the port's owner/trust model against Towny's
 * personal-plot-owner, town-mayor and town-resident concepts.
 *
 * <p>Ownership follows Towny's two plot shapes. A plot with a personal owner
 * ({@code TownBlock.getResidentOrNull()}) is owned by that resident. A town-owned plot has no personal owner, so
 * for our purposes it is owned by the town mayor ({@code Town.getMayor()}). A warp on the town's communal land
 * is the mayor's to place. Trust widens ownership to town membership ({@code Town.hasResident(UUID)}), matching
 * the owner-or-member reading the other providers use. A ban is a Towny outlaw: {@link ClaimLookup#isBanned}
 * resolves the resident with {@code TownyAPI.getResident(UUID)} and asks the owning town
 * {@code Town.hasOutlaw(Resident)}.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code com.palmergames} type: a server without Towny loads none of its classes. The Towny API
 * chain runs lazily inside {@link #claimAt} past that guard, and any reflective failure logs once and degrades
 * to empty rather than propagating.
 */
@NullMarked
public final class TownyClaimProvider implements ClaimProvider {

    private static final String TOWNY = "Towny";
    private static final String API_CLASS = "com.palmergames.bukkit.towny.TownyAPI";
    private static final String RESIDENT_CLASS = "com.palmergames.bukkit.towny.object.Resident";

    // Towny plots are 2D chunk-keyed claims, a warp is on a plot regardless of height, so a constant Y keeps
    // the lookup off getHighestBlockYAt(), which is region-bound and unsafe on Folia, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public TownyClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin towny = server.getPluginManager().getPlugin(TOWNY);
        return towny != null && towny.isEnabled();
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
            Object api = townyApi();
            Location location = new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ);
            Object townBlock = townBlockAt(api, location);
            if (townBlock == null) {
                return Optional.empty();
            }
            return Optional.of(new TownyClaimLookup(new ReflectivePlotView(api, townBlock)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** {@code TownyAPI.getInstance()}: the API singleton, reached only past the plugin-present guard. */
    private static Object townyApi() throws ReflectiveOperationException {
        return Class.forName(API_CLASS).getMethod("getInstance").invoke(null);
    }

    /** {@code TownyAPI.getTownBlock(Location)}: the plot covering the block, or {@code null} in wilderness. */
    private static @Nullable Object townBlockAt(Object api, Location location) throws ReflectiveOperationException {
        return api.getClass().getMethod("getTownBlock", Location.class).invoke(api, location);
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection, and
     * a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=towny reason={}", failure.toString());
        }
    }

    /**
     * One Towny town plot's owner/member/outlaw answers, decoupled from reflection so the personal-owner versus
     * town-owned ownership fold and the outlaw ban can be exercised without a live Towny.
     */
    interface PlotView {

        /** The plot's personal owner, or empty when the plot is town-owned (no personal owner). */
        Optional<UUID> personalOwner();

        /** The owning town's mayor, or empty when there is no town or mayor. */
        Optional<UUID> mayor();

        /** Whether {@code player} is a resident (member) of the owning town. */
        boolean isResident(UUID player);

        /** Whether {@code player} is an outlaw of the owning town, Towny's per-town ban. */
        boolean isOutlaw(UUID player);
    }

    /** Reflective {@link PlotView} over a Towny {@code TownBlock}, degrading to empty/false on any failure. */
    private final class ReflectivePlotView implements PlotView {

        private final Object api;
        private final Object townBlock;

        private ReflectivePlotView(Object api, Object townBlock) {
            this.api = api;
            this.townBlock = townBlock;
        }

        @Override
        public Optional<UUID> personalOwner() {
            try {
                Object resident =
                        townBlock.getClass().getMethod("getResidentOrNull").invoke(townBlock);
                return residentUuid(resident);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        @Override
        public Optional<UUID> mayor() {
            try {
                Object town = townOrNull();
                if (town == null) {
                    return Optional.empty();
                }
                return residentUuid(town.getClass().getMethod("getMayor").invoke(town));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        @Override
        public boolean isResident(UUID player) {
            try {
                Object town = townOrNull();
                if (town == null) {
                    return false;
                }
                Object answer =
                        town.getClass().getMethod("hasResident", UUID.class).invoke(town, player);
                return Boolean.TRUE.equals(answer);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }

        @Override
        public boolean isOutlaw(UUID player) {
            try {
                Object resident =
                        api.getClass().getMethod("getResident", UUID.class).invoke(api, player);
                Object town = townOrNull();
                if (resident == null || town == null) {
                    return false;
                }
                Class<?> residentClass = Class.forName(RESIDENT_CLASS);
                Object answer =
                        town.getClass().getMethod("hasOutlaw", residentClass).invoke(town, resident);
                return Boolean.TRUE.equals(answer);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return false;
            }
        }

        private @Nullable Object townOrNull() throws ReflectiveOperationException {
            return townBlock.getClass().getMethod("getTownOrNull").invoke(townBlock);
        }

        private Optional<UUID> residentUuid(@Nullable Object resident) throws ReflectiveOperationException {
            if (resident == null) {
                return Optional.empty();
            }
            Object uuid = resident.getClass().getMethod("getUUID").invoke(resident);
            return uuid instanceof UUID id ? Optional.of(id) : Optional.empty();
        }
    }

    /** Adapts a Towny town plot to the port's read-only claim view. */
    record TownyClaimLookup(PlotView plot) implements ClaimLookup {

        TownyClaimLookup {
            Objects.requireNonNull(plot, "plot");
        }

        @Override
        public boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            Optional<UUID> personal = plot.personalOwner();
            if (personal.isPresent()) {
                return personal.get().equals(player);
            }
            // A town-owned plot has no personal owner, so the town mayor stands in as its owner.
            return plot.mayor().map(player::equals).orElse(false);
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            return isOwner(player) || plot.isResident(player);
        }

        @Override
        public Optional<UUID> owner() {
            Optional<UUID> personal = plot.personalOwner();
            return personal.isPresent() ? personal : plot.mayor();
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            return plot.isOutlaw(player);
        }
    }
}
