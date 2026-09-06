package com.uxplima.uxmessentials.migration.convert.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The single seam where {@code java.sql} touches a foreign database for a JDBC-backed import source, the
 * DB-source analogue of the EssentialsX source's {@code YamlSource} (docs/12-migration §2). It opens a
 * <em>read-only</em> {@link Connection} from a {@link JdbcConnection} and reads one table's rows through a
 * caller-supplied SQL string and {@link RowMapper}. Connection, statement and result set are all closed by
 * try-with-resources before the call returns, so a source can never leak a handle.
 *
 * <p>The rows of one query are collected into a list inside the try-with-resources block rather than handed
 * back as a live cursor: a moderation table (bans/mutes/warnings) is small relative to the streamed
 * 50 000-file userdata tree (§7), and collecting eagerly is what lets the connection close immediately
 * without a half-drained {@code ResultSet} pinning it open. The plan still composes these per-table lists
 * lazily, so the import stays bounded across tables.
 *
 * <p>This class builds no SQL: the caller passes a fully-formed statement string (with any table name
 * already strictly sanitised: a table name cannot be a bind parameter) and binds only literal values. No
 * value reaches the SQL by concatenation.
 */
@NullMarked
public final class JdbcSource {

    private JdbcSource() {}

    /**
     * Open a read-only connection, run {@code sql}, and map every row through {@code mapper}.
     *
     * @param details the connection coordinates
     * @param sql the prepared statement text (table name pre-sanitised; values are bound, never concatenated)
     * @param mapper the per-row mapper
     * @return the mapped rows, in result-set order
     * @throws SQLException if the connection, statement, or a row read fails
     */
    public static <R> List<R> query(JdbcConnection details, String sql, RowMapper<R> mapper) throws SQLException {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(mapper, "mapper");
        try (Connection connection = open(details);
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            List<R> mapped = new ArrayList<>();
            while (rows.next()) {
                mapped.add(mapper.map(rows));
            }
            return mapped;
        }
    }

    /** Probe whether the configured database can be opened at all: the source's {@code detect} read. */
    public static boolean canOpen(JdbcConnection details) {
        Objects.requireNonNull(details, "details");
        try (Connection connection = open(details)) {
            return connection.isValid(5);
        } catch (SQLException unreachable) {
            return false;
        }
    }

    private static Connection open(JdbcConnection details) throws SQLException {
        Connection connection = details.username().isPresent()
                ? DriverManager.getConnection(
                        details.url(),
                        details.username().orElseThrow(),
                        details.password().orElse(""))
                : DriverManager.getConnection(details.url());
        // Read-only is advisory across drivers, but it signals intent and lets a driver short-circuit write
        // bookkeeping; the import never issues a write through this connection regardless.
        connection.setReadOnly(true);
        return connection;
    }
}
