package com.uxplima.uxmessentials.migration.convert.olzie.parse;

import com.uxplima.uxmessentials.migration.convert.jdbc.RowMapper;
import org.jspecify.annotations.NullMarked;

/**
 * The per-row readers the Olzie source binds to its {@link OlzieTables} statements, the {@code java.sql} seam from a
 * {@link java.sql.ResultSet} row to a parse record, one factory per table. Each reads columns by name and never
 * advances the cursor (the {@code JdbcSource} owns iteration). The two boolean access flags are stored as integers by
 * Olzie, so they are read as {@code int} and folded to {@code true} for any non-zero value; a nullable text column is
 * read straight through and may come back {@code null}, and the mapper decides what an absent facet means.
 */
@NullMarked
public final class OlzieRowReaders {

    private OlzieRowReaders() {}

    /** Read a warp row into an {@link OlzieWarpRow}. */
    public static RowMapper<OlzieWarpRow> warp() {
        return row -> new OlzieWarpRow(
                row.getLong("id"),
                row.getString("uuid"),
                row.getString("world"),
                row.getDouble("x"),
                row.getDouble("y"),
                row.getDouble("z"),
                row.getFloat("yaw"),
                row.getFloat("pitch"),
                row.getString("name"),
                row.getString("description"),
                row.getString("category"),
                row.getString("icon"),
                row.getLong("visits"),
                row.getLong("date"),
                row.getDouble("cost"),
                row.getString("password"),
                row.getInt("whitelist_enabled") != 0,
                row.getInt("locked") != 0);
    }

    /** Read a whitelist / manager / favourite row into an {@link OlziePlayerRow}. */
    public static RowMapper<OlziePlayerRow> playerRow() {
        return row -> new OlziePlayerRow(row.getLong("warp_id"), row.getString("player_uuid"));
    }

    /** Read a ban row into an {@link OlzieBanRow}. */
    public static RowMapper<OlzieBanRow> banRow() {
        return row -> new OlzieBanRow(
                row.getLong("warp_id"), row.getString("player_uuid"), row.getLong("time"), row.getString("reason"));
    }

    /** Read a rating row into an {@link OlzieRatingRow}. */
    public static RowMapper<OlzieRatingRow> ratingRow() {
        return row -> new OlzieRatingRow(row.getLong("warp_id"), row.getString("uuid"), row.getInt("rate"));
    }

    /** Read a grouped distinct-visitor row into an {@link OlzieVisitRow}. */
    public static RowMapper<OlzieVisitRow> visitRow() {
        return row -> new OlzieVisitRow(row.getLong("warp_id"), row.getInt("uniq"));
    }
}
