package com.uxplima.uxmessentials.migration.convert.litebans.parse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.convert.jdbc.RowMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link RowMapper}s that read a LiteBans punishment row into a {@link LiteBansRow}. There are two: the
 * ban/mute reader (no {@code warned} column) and the warning reader (which adds it). Both read columns by
 * name so the reader is insensitive to column order. A {@code BIT} discriminator is read through
 * {@link ResultSet#getBoolean}, H2, MySQL/MariaDB and PostgreSQL all map a BIT(1)/BOOLEAN to a boolean.
 *
 * <p>This is parse-layer only: it builds a {@link LiteBansRow} value and never a domain type. Translation to
 * a {@code :core} sanction happens in {@code map/}.
 */
@NullMarked
public final class LiteBansRowReaders {

    private LiteBansRowReaders() {}

    /** Reader for a {@code litebans_bans} or {@code litebans_mutes} row (no {@code warned} column). */
    public static RowMapper<LiteBansRow> punishment() {
        return row -> read(row, Optional.empty());
    }

    /** Reader for a {@code litebans_warnings} row, which carries the extra {@code warned} BIT column. */
    public static RowMapper<LiteBansRow> warning() {
        return row -> read(row, Optional.of(row.getBoolean("warned")));
    }

    private static LiteBansRow read(ResultSet row, Optional<Boolean> warned) throws SQLException {
        return new LiteBansRow(
                stringOrEmpty(row.getString("uuid")),
                optional(row.getString("ip")),
                optional(row.getString("reason")),
                stringOrEmpty(row.getString("banned_by_uuid")),
                optional(row.getString("banned_by_name")),
                row.getLong("time"),
                row.getLong("until"),
                row.getBoolean("ipban"),
                row.getBoolean("ipban_wildcard"),
                row.getBoolean("active"),
                warned);
    }

    private static String stringOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static Optional<String> optional(@Nullable String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
