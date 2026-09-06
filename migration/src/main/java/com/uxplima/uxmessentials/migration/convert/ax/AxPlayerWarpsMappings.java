package com.uxplima.uxmessentials.migration.convert.ax;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The AxPlayerWarps source's {@code SupportedMappings} rows: its claim of what it migrates (docs/12-migration §5).
 * Each row ties an AxPlayerWarps table to the player-warp facet it seeds, the mapper that performs the translation, and
 * the identity the write path keys on. The single {@code AxWarpMapper} produces the whole {@code ImportedPlayerWarp};
 * the side rows (ratings, whitelist, blacklist, favourites, visits) ride along on it into their stores, so they name
 * that mapper too. {@code MigrationSourceRegistryDriftTest} holds this table in lock-step with the registered source.
 */
@NullMarked
public final class AxPlayerWarpsMappings {

    private static final List<MappingRow> ROWS = List.of(
            new MappingRow("axplayerwarps_warps", "PlayerWarp", "playerwarps", "AxWarpMapper", "name"),
            new MappingRow("axplayerwarps_ratings", "RatingSummary", "playerwarps", "AxWarpMapper", "(warp, player)"),
            new MappingRow(
                    "axplayerwarps_whitelisted", "WarpWhitelist", "playerwarps", "AxWarpMapper", "(warp, player)"),
            new MappingRow("axplayerwarps_blacklisted", "BanRecord", "playerwarps", "AxWarpMapper", "(warp, player)"),
            new MappingRow("axplayerwarps_favorites", "WarpFavourite", "playerwarps", "AxWarpMapper", "(player, warp)"),
            new MappingRow("axplayerwarps_visits", "VisitSummary", "playerwarps", "AxWarpMapper", "warp"));

    private AxPlayerWarpsMappings() {}

    /** The AxPlayerWarps mapping rows, in doc order. */
    public static List<MappingRow> rows() {
        return ROWS;
    }
}
