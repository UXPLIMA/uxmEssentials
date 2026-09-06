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
 * Covers V70 ({@code player_warps} rebuilt around a surrogate id, plus the nine side tables) the way the
 * established migration tests cover their version, against the default embedded SQLite backend. It proves the
 * rebuilt table carries the new typed columns and a full row round-trips, that every one of the nine side tables
 * exists, that the globally-unique {@code name} constraint rejects a duplicate under a different owner, and, the
 * point of a rename-not-drop rebuild. That the two legacy tables survive under their {@code _v1_legacy} names so
 * the one-shot data migration can still read them.
 *
 * <p>Infrastructure mirrors {@code WarpCategoriesMigrationTest}: it reuses the production
 * {@link DataSourceFactory}/{@link DatabaseSettings} over a temp-folder SQLite database and drives Flyway directly
 * with {@link MigrationVersion} targets.
 */
class PlayerWarpsRebuildMigrationTest {

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration";
    private static final String REBUILD_VERSION = "70";
    private static final String[] SIDE_TABLES = {
        "player_warp_ratings",
        "player_warp_visits",
        "player_warp_bans",
        "player_warp_whitelist",
        "player_warp_members",
        "player_warp_favourites",
        "player_warp_payments",
        "player_warp_rating_rewards",
        "player_warp_pending_teleports"
    };

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
    void v70RebuildsPlayerWarpsWithTheNewTypedColumns() {
        migrateTo(REBUILD_VERSION);

        insertWarp(1L, "base", "11111111-1111-1111-1111-111111111111");

        Record row = dsl.select()
                .from(DSL.table("player_warps"))
                .where(DSL.field("id").eq(1L))
                .fetchOne();
        assertThat(row)
                .as("the inserted player_warps row is readable after V70")
                .isNotNull();
        assertThat(row.get("name", String.class)).isEqualTo("base");
        assertThat(row.get("access", String.class)).isEqualTo("PUBLIC");
        assertThat(row.get("status", String.class)).isEqualTo("ACTIVE");
        assertThat(row.get("category_id", String.class)).isEqualTo("pvp");
        assertThat(row.get("rating_score", Double.class)).isEqualTo(4.5);
        assertThat(row.get("sponsored_until", Long.class)).isEqualTo(9_000L);
        assertThat(row.get("updated_at", Long.class)).isEqualTo(2_000L);
    }

    @Test
    void v70CreatesEveryOneOfTheNineSideTables() {
        migrateTo(REBUILD_VERSION);

        for (String sideTable : SIDE_TABLES) {
            assertThatCode(() -> dsl.select(DSL.field("warp_id"))
                            .from(DSL.table(sideTable))
                            .fetch())
                    .as("side table %s exists after V70", sideTable)
                    .doesNotThrowAnyException();
        }

        // A representative side table round-trips its non-key columns, not just warp_id.
        dsl.transaction(configuration -> DSL.using(configuration)
                .insertInto(DSL.table("player_warp_ratings"))
                .set(DSL.field("warp_id"), 1L)
                .set(DSL.field("player_uuid"), "22222222-2222-2222-2222-222222222222")
                .set(DSL.field("stars"), 5)
                .set(DSL.field("rated_at"), 1_234L)
                .execute());
        Record rating = dsl.select()
                .from(DSL.table("player_warp_ratings"))
                .where(DSL.field("warp_id").eq(1L))
                .fetchOne();
        assertThat(rating).isNotNull();
        assertThat(rating.get("stars", Integer.class)).isEqualTo(5);
    }

    @Test
    void theNameConstraintIsGloballyUniqueAcrossOwners() {
        migrateTo(REBUILD_VERSION);
        insertWarp(1L, "base", "11111111-1111-1111-1111-111111111111");

        assertThatCode(() -> insertWarp(2L, "base", "33333333-3333-3333-3333-333333333333"))
                .as("the name unique constraint rejects a second warp named the same, even under another owner")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
    }

    @Test
    void theLegacyTablesSurviveForTheDataMigration() {
        migrateTo(REBUILD_VERSION);

        assertThatCode(() -> dsl.select(DSL.field("owner"))
                        .from(DSL.table("player_warps_v1_legacy"))
                        .fetch())
                .as("player_warps_v1_legacy still exists after V70")
                .doesNotThrowAnyException();
        assertThatCode(() -> dsl.select(DSL.field("owner_uuid"))
                        .from(DSL.table("player_warp_ratings_v1_legacy"))
                        .fetch())
                .as("player_warp_ratings_v1_legacy still exists after V70")
                .doesNotThrowAnyException();
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

    private void insertWarp(long id, String name, String owner) {
        dsl.transaction(configuration -> DSL.using(configuration)
                .insertInto(DSL.table("player_warps"))
                .set(DSL.field("id"), id)
                .set(DSL.field("name"), name)
                .set(DSL.field("owner"), owner)
                .set(DSL.field("world"), "00000000-0000-0000-0000-000000000000")
                .set(DSL.field("world_name"), "world")
                .set(DSL.field("x"), 1.0)
                .set(DSL.field("y"), 64.0)
                .set(DSL.field("z"), 1.0)
                .set(DSL.field("yaw"), 0.0f)
                .set(DSL.field("pitch"), 0.0f)
                .set(DSL.field("category_id"), "pvp")
                .set(DSL.field("access"), "PUBLIC")
                .set(DSL.field("status"), "ACTIVE")
                .set(DSL.field("rating_score"), 4.5)
                .set(DSL.field("sponsored_until"), 9_000L)
                .set(DSL.field("created_at"), 1_000L)
                .set(DSL.field("updated_at"), 2_000L)
                .execute());
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
