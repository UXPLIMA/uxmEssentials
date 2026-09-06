package com.uxplima.uxmessentials.migration.convert.litebans;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcConnection;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcSource;
import com.uxplima.uxmessentials.migration.convert.jdbc.RowMapper;
import com.uxplima.uxmessentials.migration.convert.litebans.map.LiteBansBanMapper;
import com.uxplima.uxmessentials.migration.convert.litebans.map.LiteBansMuteMapper;
import com.uxplima.uxmessentials.migration.convert.litebans.map.LiteBansWarnMapper;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansRow;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansRowReaders;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansTables;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The LiteBans source's {@link ImportPlan}. It composes three sub-streams (bans, mutes, warnings) into one
 * {@link ImportRecord} stream, each table read and mapped on demand as the importer's bounded executor drains
 * it (docs/12-migration §7). Each table is queried through {@link JdbcSource}, which opens and closes its own
 * read-only connection per query, so no handle is held across records.
 *
 * <p>Resilience mirrors the EssentialsX source's "one bad record is skipped, never fatal" rule (§4): a table
 * that fails to query (missing, or the database unreachable) is logged and yields no rows rather than
 * aborting the run, and a single row that fails to map is dropped so one malformed sanction never stops the
 * stream. Only currently-meaningful rows are imported: a row with {@code active = false} (a manually-removed
 * punishment) is filtered out, so the import seeds the live sanctions a LiteBans server is actually enforcing.
 */
@NullMarked
final class LiteBansPlan implements ImportPlan {

    private final JdbcConnection connection;
    private final LiteBansTables tables;
    private final LiteBansBanMapper banMapper;
    private final LiteBansMuteMapper muteMapper;
    private final LiteBansWarnMapper warnMapper;
    private final Logger log;

    LiteBansPlan(JdbcConnection connection, LiteBansTables tables, Logger log) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.tables = Objects.requireNonNull(tables, "tables");
        this.log = Objects.requireNonNull(log, "log");
        this.banMapper = new LiteBansBanMapper();
        this.muteMapper = new LiteBansMuteMapper();
        this.warnMapper = new LiteBansWarnMapper();
    }

    @Override
    public Stream<ImportRecord> records() {
        Stream<ImportRecord> bans =
                table(() -> rows(tables.selectBans(), LiteBansRowReaders.punishment()), banMapper::map);
        Stream<ImportRecord> mutes =
                table(() -> rows(tables.selectMutes(), LiteBansRowReaders.punishment()), muteMapper::map);
        Stream<ImportRecord> warns =
                table(() -> rows(tables.selectWarnings(), LiteBansRowReaders.warning()), warnMapper::map);
        return Stream.concat(bans, Stream.concat(mutes, warns));
    }

    /** Defer a table's query into the stream so it runs only when this segment is drained (§7). */
    private Stream<ImportRecord> table(
            Supplier<List<LiteBansRow>> query, java.util.function.Function<LiteBansRow, Optional<ImportRecord>> map) {
        return Stream.<Supplier<List<LiteBansRow>>>of(query)
                .flatMap(deferred -> deferred.get().stream())
                .filter(LiteBansRow::active)
                .flatMap(row -> mapRow(row, map));
    }

    private Stream<ImportRecord> mapRow(
            LiteBansRow row, java.util.function.Function<LiteBansRow, Optional<ImportRecord>> map) {
        try {
            return map.apply(row).stream();
        } catch (RuntimeException badRow) {
            // One malformed sanction is dropped, never fatal (docs/12-migration §4).
            log.warn("skipping a malformed LiteBans row: {}", String.valueOf(badRow.getMessage()));
            return Stream.empty();
        }
    }

    private List<LiteBansRow> rows(String sql, RowMapper<LiteBansRow> reader) {
        try {
            return JdbcSource.query(connection, sql, reader);
        } catch (SQLException unreadable) {
            // A missing table or an unreachable database yields no rows; the run continues with the rest.
            log.warn("LiteBans query failed, skipping table: {}", String.valueOf(unreadable.getMessage()));
            return List.of();
        }
    }

    @Override
    public void close() {
        // Each table query opens and closes its own connection; nothing is held open across records.
    }
}
