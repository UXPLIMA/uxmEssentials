package com.uxplima.uxmessentials.migration.convert.athelion;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The Athelion source's {@code SupportedMappings} rows: its claim of what it migrates (docs/12-migration §5). Athelion
 * keeps all warp state in one serialised {@code data.yml}, so. Unlike the JDBC AxPlayerWarps source with its side tables
 *: the ratings and blocked-players ride inside each warp entry. The single {@code AthelionWarpMapper} produces the whole
 * {@code ImportedPlayerWarp}; the two side facets it carries (the reconstructed ratings and the blocked-players as bans)
 * name that same mapper. {@code MigrationSourceRegistryDriftTest} holds this table in lock-step with the registered
 * source.
 */
@NullMarked
public final class AthelionPlayerWarpsMappings {

    private static final List<MappingRow> ROWS = List.of(
            new MappingRow("data.yml warps.<id>", "PlayerWarp", "playerwarps", "AthelionWarpMapper", "name"),
            new MappingRow(
                    "warps.<id>.ratings+reviewers",
                    "RatingSummary",
                    "playerwarps",
                    "AthelionWarpMapper",
                    "(warp, player)"),
            new MappingRow(
                    "warps.<id>.blocked-players", "BanRecord", "playerwarps", "AthelionWarpMapper", "(warp, player)"));

    private AthelionPlayerWarpsMappings() {}

    /** The Athelion mapping rows, in doc order. */
    public static List<MappingRow> rows() {
        return ROWS;
    }
}
