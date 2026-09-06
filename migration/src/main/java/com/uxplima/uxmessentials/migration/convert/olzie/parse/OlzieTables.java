package com.uxplima.uxmessentials.migration.convert.olzie.parse;

import org.jspecify.annotations.NullMarked;

/**
 * The read-only {@code SELECT} statements the Olzie PlayerWarps source runs, held as constants so no table name is
 * ever concatenated from input. Olzie uses a fixed {@code playerwarps_} table set (it has no configurable prefix), so
 *, unlike the LiteBans source, there is nothing to sanitise: these are compile-time literal statements that bind
 * nothing.
 *
 * <p>The warps table carries no explicit id column, so its SQLite {@code rowid} is surfaced as {@code id} and is the
 * key every side table's {@code warp_id} references. The warp query reads only the columns the mapper carries; Olzie's
 * {@code sponsor}, {@code sponsor_cooldown} and {@code last_rent} have no home in the shared neutral import record (the
 * writer seeds sponsorship and rent empty for every source), and {@code type}, {@code random_sort},
 * {@code earned_rate_rewards}, {@code set_prices} and {@code tags} are deliberately not migrated, so none of those
 * columns is selected.
 */
@NullMarked
public final class OlzieTables {

    /**
     * Every warp with its owner uuid, world name and access-deriving flags inline. The {@code rowid} is aliased to
     * {@code id} because Olzie's warps table has no surrogate id column of its own.
     */
    public static final String SELECT_WARPS = """
            SELECT rowid AS id, uuid, world, x, y, z, yaw, pitch, name, description, category, icon,
                   visits, date, cost, password, whitelist_enabled, locked
            FROM playerwarps_warps""";

    /** Every per-vote star rating; the reviewer is a uuid text and the free-text review is read past. */
    public static final String SELECT_RATES = "SELECT warp_id, uuid, rate FROM playerwarps_rates";

    /** Every whitelist entry. */
    public static final String SELECT_WHITELIST = "SELECT warp_id, player_uuid FROM playerwarps_warps_whitelisted";

    /** Every manager entry, mapped to a warp member with the manager role. */
    public static final String SELECT_MANAGERS = "SELECT warp_id, player_uuid FROM playerwarps_warps_managers";

    /** Every ban, with its reason and imposed-at instant. */
    public static final String SELECT_BANNED =
            "SELECT warp_id, player_uuid, time, reason FROM playerwarps_warps_banned";

    /** Every favourite entry. */
    public static final String SELECT_FAVOURITES =
            "SELECT warp_id, player_uuid FROM playerwarps_players_favourite_warps";

    /** The distinct-visitor count per warp, aggregated so the visit log is read once, not per warp. */
    public static final String SELECT_VISITS = """
            SELECT warp_id, COUNT(DISTINCT player_uuid) AS uniq
            FROM playerwarps_warps_visits GROUP BY warp_id""";

    private OlzieTables() {}
}
