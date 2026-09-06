package com.uxplima.uxmessentials.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DataSourceFactory;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseSettings;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers V64 ({@code playerstate_playtime}) like the established migration tests cover V62/V63: it proves the
 * fresh-table migration applies cleanly from the version before it, that the per-day ledger round-trips a row
 * through the new schema, and that the composite {@code (uuid, day)} primary key holds (a second insert on the
 * same key is rejected rather than duplicated). Unlike V62 this is a plain {@code CREATE TABLE} with no backfill,
 * so the test is the apply-and-round-trip shape rather than the seed-then-rebuild shape.
 *
 * <p>Infrastructure mirrors {@code StaffLoadoutBackfillMigrationTest}: it reuses the production
 * {@link DataSourceFactory}/{@link DatabaseSettings} to build the real embedded-SQLite pool over a temp-folder
 * database, and drives Flyway directly with {@link MigrationVersion} targets via {@link #migrateTo} so it can stop
 * at the version before V64, confirm the table is absent, then apply V64 and exercise the new schema.
 */
class PlaytimeMigrationTest {

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration";
    private static final String VERSION_BEFORE = "63";
    private static final String PLAYTIME_VERSION = "64";

    private HikariDataSource dataSource;
    private DSLContext dsl;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        dataSource = DataSourceFactory.create(new DatabaseSettings(new SqliteConfig(), dataFolder));
        dsl = DSL.using(dataSource, SQLDialect.SQLITE);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void theTableDoesNotExistBeforeV64() {
        migrateTo(VERSION_BEFORE);

        assertThatCode(() -> dsl.select(DSL.field("uuid"))
                        .from(DSL.table("playerstate_playtime"))
                        .fetch())
                .as("playerstate_playtime does not exist before the V64 migration")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
    }

    @Test
    void v64CreatesTheLedgerAndItRoundTripsARow() {
        migrateTo(PLAYTIME_VERSION);
        UUID player = UUID.randomUUID();

        insertRow(player, "2026-06-22", 120L, 30L);

        Record row = loadRow(player, "2026-06-22");
        assertThat(row).as("the inserted ledger row is readable after V64").isNotNull();
        assertThat(row.get("active_seconds", Long.class)).isEqualTo(120L);
        assertThat(row.get("afk_seconds", Long.class)).isEqualTo(30L);
    }

    @Test
    void theCompositeKeyRejectsADuplicatePlayerDay() {
        migrateTo(PLAYTIME_VERSION);
        UUID player = UUID.randomUUID();
        insertRow(player, "2026-06-22", 60L, 0L);

        assertThatCode(() -> insertRow(player, "2026-06-22", 60L, 0L))
                .as("the (uuid, day) primary key rejects a second raw insert on the same key")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
    }

    /** Run Flyway forward up to and including {@code version}, then stop. */
    private void migrateTo(String version) {
        Flyway.configure(getClass().getClassLoader())
                .dataSource(dataSource)
                .locations(MIGRATIONS_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private void insertRow(UUID player, String day, long active, long afk) {
        // Wrap the seed in a transaction so it commits on the single-writer SQLite pool (autoCommit off).
        dsl.transaction(configuration -> DSL.using(configuration)
                .insertInto(DSL.table("playerstate_playtime"))
                .set(DSL.field("uuid"), player.toString())
                .set(DSL.field("day"), day)
                .set(DSL.field("active_seconds"), active)
                .set(DSL.field("afk_seconds"), afk)
                .execute());
    }

    private Record loadRow(UUID player, String day) {
        return dsl.select()
                .from(DSL.table("playerstate_playtime"))
                .where(DSL.field("uuid").eq(player.toString()))
                .and(DSL.field("day").eq(day))
                .fetchOne();
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
