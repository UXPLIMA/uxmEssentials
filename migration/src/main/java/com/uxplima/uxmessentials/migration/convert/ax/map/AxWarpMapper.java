package com.uxplima.uxmessentials.migration.convert.ax.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.ax.parse.AxPlayer;
import com.uxplima.uxmessentials.migration.convert.ax.parse.AxWarpRow;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Translates an AxPlayerWarps warp row into the competitor-neutral {@link ImportedPlayerWarp} the shared writer lands
 * (docs/12-migration §5). The scalar work lives here: the {@code Access} ordinal becomes a {@link WarpAccess} (the
 * source has no password gate, so a warp is only ever public / whitelist / private), the per-warp currency collapses
 * to the server default (AxPlayerWarps stores an economy-provider hook name, not one of our currency ids), and the
 * source name is kept as the display label so its original casing survives the lowercase-id sanitising the writer does.
 *
 * <p>A warp whose world the live server does not know is dropped. The mapper returns {@link Optional#empty()} and the
 * plan counts it as skipped. Exactly as the EssentialsX warp mapper drops an unknown world, so a migrated warp always
 * has a resolvable destination. The owner and the side-list players are resolved to uuids by the plan through the
 * player map before they reach this mapper.
 */
@NullMarked
public final class AxWarpMapper {

    /** The currency imported prices are recorded in; the source's per-warp hook name does not map to our registry. */
    private static final String DEFAULT_CURRENCY = "default";

    private final WorldNameResolver worlds;

    public AxWarpMapper(WorldNameResolver worlds) {
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /** Map one warp, or empty when its world does not resolve on the live server. */
    public Optional<ImportedPlayerWarp> map(AxWarpRow row, AxPlayer owner, Sidecar side) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(side, "side");
        String worldName = row.world();
        if (worldName == null) {
            return Optional.empty();
        }
        return worlds.resolve(worldName).map(world -> build(row, owner, world, side));
    }

    private ImportedPlayerWarp build(AxWarpRow row, AxPlayer owner, WorldRef world, Sidecar side) {
        Position location = new Position(world, row.x(), row.y(), row.z(), row.yaw(), row.pitch());
        return new ImportedPlayerWarp(
                UUID.fromString(owner.uuid()),
                owner.name(),
                row.name(),
                Optional.of(row.name()),
                location,
                Optional.ofNullable(row.category()),
                Optional.ofNullable(row.description()),
                Optional.ofNullable(row.material()),
                access(row.access()),
                Optional.empty(),
                BigDecimal.valueOf(row.price()),
                DEFAULT_CURRENCY,
                BigDecimal.valueOf(row.earnedMoney()),
                Instant.ofEpochMilli(row.created()),
                side.visits(),
                side.uniqueVisitors(),
                side.ratings(),
                List.of(),
                side.whitelist(),
                side.bans(),
                side.favourites());
    }

    /**
     * Map the source's {@code Access} ordinal to our access axis: {@code 0 -> PUBLIC}, {@code 1 -> WHITELIST}
     * ({@code WHITELISTED}), {@code 2 -> PRIVATE}. AxPlayerWarps has no password access, so {@code PASSWORD} never
     * appears; an unrecognised ordinal falls back to the closed {@code PRIVATE} rather than leaking a warp public.
     */
    private static WarpAccess access(int ordinal) {
        return switch (ordinal) {
            case 0 -> WarpAccess.PUBLIC;
            case 1 -> WarpAccess.WHITELIST;
            default -> WarpAccess.PRIVATE;
        };
    }

    /**
     * The resolved side-table data for one warp. The members-less AxPlayerWarps social state, gathered and keyed to
     * uuids by the plan before mapping.
     *
     * @param whitelist the whitelisted players
     * @param bans the warp bans (from the source's blacklist)
     * @param favourites the players who starred the warp
     * @param ratings the per-vote ratings
     * @param visits the total visit count
     * @param uniqueVisitors the distinct-visitor count
     */
    public record Sidecar(
            List<UUID> whitelist,
            List<ImportedPlayerWarp.Ban> bans,
            List<UUID> favourites,
            List<ImportedPlayerWarp.Rating> ratings,
            long visits,
            int uniqueVisitors) {

        public Sidecar {
            whitelist = List.copyOf(Objects.requireNonNull(whitelist, "whitelist"));
            bans = List.copyOf(Objects.requireNonNull(bans, "bans"));
            favourites = List.copyOf(Objects.requireNonNull(favourites, "favourites"));
            ratings = List.copyOf(Objects.requireNonNull(ratings, "ratings"));
        }

        /** An empty sidecar: a warp with no whitelist, bans, favourites, ratings, or recorded visits. */
        public static Sidecar empty() {
            return new Sidecar(List.of(), List.of(), List.of(), List.of(), 0L, 0);
        }
    }
}
