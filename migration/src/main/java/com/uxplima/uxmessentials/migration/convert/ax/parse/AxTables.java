package com.uxplima.uxmessentials.migration.convert.ax.parse;

import org.jspecify.annotations.NullMarked;

/**
 * The read-only {@code SELECT} statements the AxPlayerWarps source runs, held as constants so no table name is ever
 * concatenated from input. AxPlayerWarps uses a fixed {@code axplayerwarps_} table set (it has no configurable
 * prefix), so, unlike the LiteBans source, there is nothing to sanitise: these are compile-time literal statements.
 * The warp query resolves the world / category / icon / currency foreign ids to their names with left joins so a warp
 * with an absent facet still returns a row; the owner id is resolved through the pre-loaded player map instead, since
 * the side tables key on that same id. Every value the reader binds afterwards travels as a bind parameter: these
 * statements bind nothing, and never interpolate a value.
 */
@NullMarked
public final class AxTables {

    /** Every player id mapped to its uuid and last-seen name. */
    public static final String SELECT_PLAYERS = "SELECT id, uuid, name FROM axplayerwarps_players";

    /** Every warp with its world / category / icon / currency names joined in; the owner stays an id. */
    public static final String SELECT_WARPS = """
            SELECT w.id AS id, w.owner_id AS owner_id, wo.world AS world,
                   w.x AS x, w.y AS y, w.z AS z, w.yaw AS yaw, w.pitch AS pitch,
                   w.name AS name, w.description AS description, c.category AS category,
                   m.material AS material, w.created AS created, cur.currency AS currency,
                   w.price AS price, w.earned_money AS earned_money, w.access AS access
            FROM axplayerwarps_warps w
            LEFT JOIN axplayerwarps_worlds wo ON w.world_id = wo.id
            LEFT JOIN axplayerwarps_categories c ON w.category_id = c.id
            LEFT JOIN axplayerwarps_materials m ON w.icon_id = m.id
            LEFT JOIN axplayerwarps_currencies cur ON w.currency_id = cur.id""";

    /** Every per-vote star rating. */
    public static final String SELECT_RATINGS = "SELECT reviewer_id, warp_id, stars, date FROM axplayerwarps_ratings";

    /** Every whitelist entry. */
    public static final String SELECT_WHITELIST = "SELECT player_id, warp_id, date FROM axplayerwarps_whitelisted";

    /** Every blacklist entry, mapped to a warp ban. */
    public static final String SELECT_BLACKLIST = "SELECT player_id, warp_id, date FROM axplayerwarps_blacklisted";

    /** Every favourite entry. */
    public static final String SELECT_FAVOURITES = "SELECT player_id, warp_id, date FROM axplayerwarps_favorites";

    /** The per-warp visit tally, aggregated so the visit log is read once, not per warp. */
    public static final String SELECT_VISITS = """
            SELECT warp_id, COUNT(*) AS total, COUNT(DISTINCT visitor_id) AS uniq
            FROM axplayerwarps_visits GROUP BY warp_id""";

    private AxTables() {}
}
