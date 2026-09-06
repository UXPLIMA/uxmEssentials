package com.uxplima.uxmessentials.migration.convert.olzie;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The Olzie source's {@code SupportedMappings} rows: its claim of what it migrates (docs/12-migration §5). Olzie is
 * the richest player-warp source: alongside the warp it seeds ratings, whitelist, bans (with a reason Ax and Athelion
 * never kept), managers (as warp members) and favourites, so it carries a row per side table. The single
 * {@code OlzieWarpMapper} produces the whole {@code ImportedPlayerWarp}; every side facet rides along on it into its
 * store, so they name that same mapper. {@code MigrationSourceRegistryDriftTest} holds this table in lock-step with the
 * registered source.
 */
@NullMarked
public final class OlziePlayerWarpsMappings {

    private static final List<MappingRow> ROWS = List.of(
            new MappingRow("playerwarps_warps", "PlayerWarp", "playerwarps", "OlzieWarpMapper", "name"),
            new MappingRow("playerwarps_rates", "RatingSummary", "playerwarps", "OlzieWarpMapper", "(warp, player)"),
            new MappingRow(
                    "playerwarps_warps_whitelisted",
                    "WarpWhitelist",
                    "playerwarps",
                    "OlzieWarpMapper",
                    "(warp, player)"),
            new MappingRow("playerwarps_warps_banned", "BanRecord", "playerwarps", "OlzieWarpMapper", "(warp, player)"),
            new MappingRow(
                    "playerwarps_warps_managers", "WarpMember", "playerwarps", "OlzieWarpMapper", "(warp, player)"),
            new MappingRow(
                    "playerwarps_players_favourite_warps",
                    "WarpFavourite",
                    "playerwarps",
                    "OlzieWarpMapper",
                    "(player, warp)"));

    private OlziePlayerWarpsMappings() {}

    /** The Olzie mapping rows, in doc order. */
    public static List<MappingRow> rows() {
        return ROWS;
    }
}
