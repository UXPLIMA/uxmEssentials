package com.uxplima.uxmessentials.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;

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
 * Pins V65 ({@code communication_announcement}): the DB-backed announcement set the {@code /announce} editor owns.
 * The table did not exist before V65, so this proves the migration creates it (a select fails at V64 and succeeds
 * after V65), that a row round-trips with the enabled flag, raw condition string, and nullable interval intact, and
 * that the id primary key rejects a duplicate. It drives Flyway directly with {@link MigrationVersion} targets via
 * {@link #migrateTo} so it can stop at V64, prove the table is absent, then apply V65. The documented way to run a
 * migration sub-range, the same shape {@code PlaytimeMigrationTest} and {@code StaffLoadoutBackfillMigrationTest}
 * use.
 */
class AnnouncementMigrationTest {

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration";
    private static final String VERSION_BEFORE = "64";
    private static final String ANNOUNCEMENT_VERSION = "65";

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
    void theTableDoesNotExistBeforeV65() {
        migrateTo(VERSION_BEFORE);

        assertThatCode(() -> dsl.select(DSL.field("id"))
                        .from(DSL.table("communication_announcement"))
                        .fetch())
                .as("communication_announcement does not exist before V65")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
    }

    @Test
    void v65CreatesTheTableAndARowRoundTrips() {
        migrateTo(ANNOUNCEMENT_VERSION);

        insertRow("tips", "first\nsecond", "CHAT,TITLE", 1, "world:hub", "entity.player.levelup", 600L);

        Record row = loadRow("tips");
        assertThat(row)
                .as("the inserted announcement row is readable after V65")
                .isNotNull();
        assertThat(row.get("lines", String.class)).isEqualTo("first\nsecond");
        assertThat(row.get("channels", String.class)).isEqualTo("CHAT,TITLE");
        assertThat(row.get("enabled", Integer.class)).isEqualTo(1);
        assertThat(row.get("display_condition", String.class)).isEqualTo("world:hub");
        assertThat(row.get("sound", String.class)).isEqualTo("entity.player.levelup");
        assertThat(row.get("interval_seconds", Long.class)).isEqualTo(600L);
    }

    @Test
    void nullableSoundAndIntervalRoundTripAsNull() {
        migrateTo(ANNOUNCEMENT_VERSION);

        insertRow("notice", "line", "CHAT", 0, "", null, null);

        Record row = loadRow("notice");
        assertThat(row.get("enabled", Integer.class)).isEqualTo(0);
        assertThat(row.get("sound", String.class)).isNull();
        assertThat(row.get("interval_seconds", Long.class)).isNull();
    }

    @Test
    void theIdPrimaryKeyRejectsADuplicate() {
        migrateTo(ANNOUNCEMENT_VERSION);
        insertRow("tips", "one", "CHAT", 1, "", null, null);

        assertThatCode(() -> insertRow("tips", "two", "CHAT", 1, "", null, null))
                .as("the id primary key forbids a second row under the same id")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
    }

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

    private void insertRow(
            String id,
            String lines,
            String channels,
            int enabled,
            String condition,
            String sound,
            Long intervalSeconds) {
        dsl.transaction(configuration -> DSL.using(configuration)
                .insertInto(DSL.table("communication_announcement"))
                .set(DSL.field("id"), id)
                .set(DSL.field("lines"), lines)
                .set(DSL.field("channels"), channels)
                .set(DSL.field("enabled"), enabled)
                .set(DSL.field("display_condition"), condition)
                .set(DSL.field("sound"), sound)
                .set(DSL.field("interval_seconds"), intervalSeconds)
                .set(DSL.field("updated_at"), 1_700_000_000_000L)
                .execute());
    }

    private Record loadRow(String id) {
        return dsl.select()
                .from(DSL.table("communication_announcement"))
                .where(DSL.field("id").eq(id))
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
