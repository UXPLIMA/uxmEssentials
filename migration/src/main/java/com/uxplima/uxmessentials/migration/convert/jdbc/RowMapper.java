package com.uxplima.uxmessentials.migration.convert.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps the {@link ResultSet}'s current row to a value of type {@code R}. A mapper reads only columns by name
 * and never advances the cursor, {@link JdbcSource} owns iteration, so a mapper stays a pure per-row read.
 * It may throw {@link SQLException}, which {@link JdbcSource} surfaces as a single failed query rather than a
 * silently dropped row.
 *
 * @param <R> the row's mapped type
 */
@FunctionalInterface
public interface RowMapper<R> {

    /** Read the cursor's current row into an {@code R}. Must not move the cursor. */
    R map(ResultSet row) throws SQLException;
}
