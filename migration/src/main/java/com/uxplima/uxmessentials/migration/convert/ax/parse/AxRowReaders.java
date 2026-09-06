package com.uxplima.uxmessentials.migration.convert.ax.parse;

import com.uxplima.uxmessentials.migration.convert.jdbc.RowMapper;
import org.jspecify.annotations.NullMarked;

/**
 * The per-row readers the AxPlayerWarps source binds to its {@link AxTables} statements, the {@code java.sql} seam
 * from a {@link java.sql.ResultSet} row to a parse record, one factory per table. Each reads columns by name and never
 * advances the cursor (the {@code JdbcSource} owns iteration). A nullable text column is read straight through and may
 * come back {@code null}; the mapper decides what an absent facet means, never this reader.
 */
@NullMarked
public final class AxRowReaders {

    private AxRowReaders() {}

    /** Read a player row into an {@link AxPlayer}. */
    public static RowMapper<AxPlayer> player() {
        return row -> new AxPlayer(row.getInt("id"), row.getString("uuid"), row.getString("name"));
    }

    /** Read a joined warp row into an {@link AxWarpRow}. */
    public static RowMapper<AxWarpRow> warp() {
        return row -> new AxWarpRow(
                row.getInt("id"),
                row.getInt("owner_id"),
                row.getString("world"),
                row.getDouble("x"),
                row.getDouble("y"),
                row.getDouble("z"),
                row.getFloat("yaw"),
                row.getFloat("pitch"),
                row.getString("name"),
                row.getString("description"),
                row.getString("category"),
                row.getString("material"),
                row.getLong("created"),
                row.getString("currency"),
                row.getDouble("price"),
                row.getDouble("earned_money"),
                row.getInt("access"));
    }

    /** Read a whitelist / blacklist / favourite row into an {@link AxAccessRow}. */
    public static RowMapper<AxAccessRow> accessRow() {
        return row -> new AxAccessRow(row.getInt("warp_id"), row.getInt("player_id"), row.getLong("date"));
    }

    /** Read a rating row into an {@link AxRatingRow}. */
    public static RowMapper<AxRatingRow> ratingRow() {
        return row -> new AxRatingRow(
                row.getInt("warp_id"), row.getInt("reviewer_id"), row.getInt("stars"), row.getLong("date"));
    }

    /** Read a grouped visit-tally row into an {@link AxVisitRow}. */
    public static RowMapper<AxVisitRow> visitRow() {
        return row -> new AxVisitRow(row.getInt("warp_id"), row.getLong("total"), row.getLong("uniq"));
    }
}
