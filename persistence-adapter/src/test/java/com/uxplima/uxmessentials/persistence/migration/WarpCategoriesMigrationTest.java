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
 * Covers V67 ({@code warp_categories} plus the {@code warps.category_id} column) the way the established
 * migration tests cover their version: it proves the table is absent before V67, that V67 creates it and a
 * category row round-trips through the new schema, that the {@code id} primary key rejects a duplicate, and that
 * the new nullable {@code category_id} column on {@code warps} accepts a category reference. This is a plain
 * {@code CREATE TABLE} + {@code ALTER TABLE} migration with no backfill, so the test is the apply-and-round-trip
 * shape rather than the seed-then-rebuild shape.
 *
 * <p>Infrastructure mirrors {@code PlaytimeMigrationTest}: it reuses the production
 * {@link DataSourceFactory}/{@link DatabaseSettings} over a temp-folder SQLite database and drives Flyway
 * directly with {@link MigrationVersion} targets so it can stop at V66, confirm the table is absent, then apply
 * V67 and exercise the new schema.
 */
class WarpCategoriesMigrationTest {

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration";
    private static final String VERSION_BEFORE = "66";
    private static final String CATEGORIES_VERSION = "67";

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
    void theTableDoesNotExistBeforeV67() {
        migrateTo(VERSION_BEFORE);

        assertThatCode(() -> dsl.select(DSL.field("id"))
                        .from(DSL.table("warp_categories"))
                        .fetch())
                .as("warp_categories does not exist before the V67 migration")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
    }

    @Test
    void v67CreatesTheTableAndItRoundTripsACategoryRow() {
        migrateTo(CATEGORIES_VERSION);

        insertCategory("pvp", "PvP Arenas", "DIAMOND_SWORD", 3, null);

        Record row = loadCategory("pvp");
        assertThat(row).as("the inserted category row is readable after V67").isNotNull();
        assertThat(row.get("display_name", String.class)).isEqualTo("PvP Arenas");
        assertThat(row.get("display_material", String.class)).isEqualTo("DIAMOND_SWORD");
        assertThat(row.get("slot", Integer.class)).isEqualTo(3);
        assertThat(row.get("parent_category_id", String.class)).isNull();
    }

    @Test
    void thePrimaryKeyRejectsADuplicateCategoryId() {
        migrateTo(CATEGORIES_VERSION);
        insertCategory("pvp", "PvP", null, 0, null);

        assertThatCode(() -> insertCategory("pvp", "PvP again", null, 0, null))
                .as("the id primary key rejects a second raw insert on the same id")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
    }

    @Test
    void v67AddsTheCategoryReferenceColumnToWarps() {
        migrateTo(CATEGORIES_VERSION);

        assertThatCode(() -> dsl.transaction(configuration -> DSL.using(configuration)
                        .insertInto(DSL.table("warps"))
                        .set(DSL.field("name"), "shop")
                        .set(DSL.field("world"), "00000000-0000-0000-0000-000000000000")
                        .set(DSL.field("world_name"), "world")
                        .set(DSL.field("x"), 1.0)
                        .set(DSL.field("y"), 64.0)
                        .set(DSL.field("z"), 1.0)
                        .set(DSL.field("yaw"), 0.0f)
                        .set(DSL.field("pitch"), 0.0f)
                        .set(DSL.field("owner"), "00000000-0000-0000-0000-000000000001")
                        .set(DSL.field("created_at"), 1_000L)
                        .set(DSL.field("category_id"), "pvp")
                        .execute()))
                .as("a warp row accepts a category_id after V67")
                .doesNotThrowAnyException();

        Record warp = dsl.select()
                .from(DSL.table("warps"))
                .where(DSL.field("name").eq("shop"))
                .fetchOne();
        assertThat(warp).isNotNull();
        assertThat(warp.get("category_id", String.class)).isEqualTo("pvp");
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

    private void insertCategory(String id, String name, String material, int slot, String parent) {
        dsl.transaction(configuration -> DSL.using(configuration)
                .insertInto(DSL.table("warp_categories"))
                .set(DSL.field("id"), id)
                .set(DSL.field("display_name"), name)
                .set(DSL.field("display_material"), material)
                .set(DSL.field("slot"), slot)
                .set(DSL.field("parent_category_id"), parent)
                .execute());
    }

    private Record loadCategory(String id) {
        return dsl.select()
                .from(DSL.table("warp_categories"))
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
