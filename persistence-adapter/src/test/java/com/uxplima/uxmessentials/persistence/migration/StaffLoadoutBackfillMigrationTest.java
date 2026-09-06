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
 * Establishes the migration-backfill test pattern and applies it to V62 ({@code staff_loadout_server_id}).
 *
 * <p>The repository tests ({@code Jooq*RepositoryTest}) open {@code Persistence}, which runs every migration to
 * head against a clean database, so they only ever exercise the head schema. A table-rebuild migration that drops
 * existing rows while rebuilding, the {@code CREATE new / INSERT…SELECT / DROP old / RENAME} shape SQLite forces,
 * since it cannot {@code ALTER} a primary key. Would still leave those tests green: there is no pre-existing row
 * to lose on a clean database. This test fills that gap. It seeds a row under the schema as it stood the version
 * before the rebuild, then runs the rebuild over it, and proves the row survives with the backfilled value rather
 * than vanishing in the {@code DROP}.
 *
 * <p>V62 re-keys {@code staff_loadout} from {@code (player)} to {@code (player, server_id)} by rebuilding the
 * table, backfilling existing rows to the default network id {@code server-1}. A pre-V62 row is a player's
 * un-restored crash-recovery loadout (the real inventory captured before staff mode, persisted precisely so a
 * crash mid-mode does not lose it), so a rebuild that drops it would lose the very items the DB-backed loadout
 * exists to protect. The assertion that closes that hole: the seeded row is still present after the rebuild, now
 * carries {@code server_id = 'server-1'}, and its opaque payload and captured scalars are byte/value-identical.
 *
 * <p>Infrastructure: the test reuses the production {@link DataSourceFactory}/{@link DatabaseSettings} to build the
 * real embedded-SQLite pool (single-writer WAL, the tested default backend) over a temp-folder database, exactly
 * as {@code Persistence.open} does. The one thing it cannot reuse is {@code Persistence.open} itself, which always
 * migrates to head in one shot with no version control. A backfill test must stop at the version before the
 * rebuild, seed, then continue. So it drives Flyway directly with {@link MigrationVersion} targets via the small
 * {@link #migrateTo} helper, the documented and intended way to run a migration sub-range.
 */
class StaffLoadoutBackfillMigrationTest {

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration";

    /** The version immediately before the rebuild: migrate here, seed an old-schema row, then continue past it. */
    private static final String VERSION_BEFORE_REBUILD = "61";

    /** The rebuild under test: {@code staff_loadout} gains {@code server_id} and a composite primary key. */
    private static final String REBUILD_VERSION = "62";

    /** The id existing rows backfill to, the default {@code network.server-id} of a single-server install. */
    private static final String BACKFILL_SERVER_ID = "server-1";

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
    void theRebuildPreservesAnExistingCrashRecoveryRowAndBackfillsItsServerId() {
        migrateTo(VERSION_BEFORE_REBUILD);

        UUID owner = UUID.randomUUID();
        OldLoadout seeded = OldLoadout.sample();
        insertPreServerIdRow(owner, seeded);

        migrateTo(REBUILD_VERSION);

        Record row = loadRowAfterRebuild(owner);
        assertThat(row)
                .as("the crash-recovery row must survive the table rebuild")
                .isNotNull();
        assertThat(row.get("server_id", String.class))
                .as("an existing row backfills to the default server id")
                .isEqualTo(BACKFILL_SERVER_ID);
        assertOriginalPayloadIntact(row, seeded);
    }

    @Test
    void migratingTheRestOfTheWayOnAFreshDatabaseStillReachesHeadWithoutTheBackfill() {
        // The backfill's INSERT…SELECT reads zero rows on a fresh database; the rebuild must still complete and
        // leave no staff_loadout row behind. This guards the seam between the two migrate calls, running the
        // rebuild over an empty table should reach head cleanly, not invent a spurious row.
        migrateTo(VERSION_BEFORE_REBUILD);
        UUID neverEnteredStaffMode = UUID.randomUUID();

        migrateTo(REBUILD_VERSION);

        assertThat(loadRowAfterRebuild(neverEnteredStaffMode)).isNull();
    }

    /**
     * Run Flyway forward up to and including {@code version}, then stop. Calling this twice with an increasing
     * target is how the test seeds a row between two schema states: the first call lands the pre-rebuild schema,
     * the second runs the rebuild over the seeded row. Baselining at {@code 0} matches the production runner so a
     * fresh database picks up V1 onward; repeated calls are idempotent for versions already applied.
     */
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

    /**
     * Insert a row in the pre-V62 column set (V29 base + the V30 {@code vanished_before} and V31
     * {@code allow_flight} flags), with no {@code server_id} column: the schema as it stands at V61. The table
     * and column names are unqualified rather than the generated jOOQ class, which reflects the head (post-V62)
     * shape and so would reference a {@code server_id} column that does not exist yet at this point.
     */
    private void insertPreServerIdRow(UUID owner, OldLoadout loadout) {
        // Wrap the seed in a transaction so it commits, the same way the repository's write path does. The pool
        // runs with autoCommit off (single-writer SQLite), so an insert left outside a transaction is rolled back
        // when the lone connection returns to the pool, and the rebuild on the next acquisition would then find
        // nothing, masking the very loss this test exists to catch.
        dsl.transaction(configuration -> DSL.using(configuration)
                .insertInto(DSL.table("staff_loadout"))
                .set(DSL.field("player"), owner.toString())
                .set(DSL.field("inventory"), loadout.inventory())
                .set(DSL.field("armor"), loadout.armor())
                .set(DSL.field("offhand"), loadout.offhand())
                .set(DSL.field("potion_effects"), loadout.potionEffects())
                .set(DSL.field("held_slot"), loadout.heldSlot())
                .set(DSL.field("exp_level"), loadout.expLevel())
                .set(DSL.field("exp_progress"), loadout.expProgress())
                .set(DSL.field("game_mode"), loadout.gameMode())
                .set(DSL.field("flying"), loadout.flying())
                .set(DSL.field("vanished_before"), loadout.vanishedBefore())
                .set(DSL.field("allow_flight"), loadout.allowFlight())
                .set(DSL.field("entered_at"), loadout.enteredAt())
                .execute());
    }

    private Record loadRowAfterRebuild(UUID owner) {
        return dsl.select()
                .from(DSL.table("staff_loadout"))
                .where(DSL.field("player").eq(owner.toString()))
                .fetchOne();
    }

    private void assertOriginalPayloadIntact(Record row, OldLoadout seeded) {
        // Every column the rebuild copies through INSERT…SELECT must arrive unchanged: the four opaque base64
        // regions byte-for-byte and the captured scalars value-for-value. server_id is the only column that
        // changes, and it is asserted separately.
        assertThat(row.get("inventory", String.class)).isEqualTo(seeded.inventory());
        assertThat(row.get("armor", String.class)).isEqualTo(seeded.armor());
        assertThat(row.get("offhand", String.class)).isEqualTo(seeded.offhand());
        assertThat(row.get("potion_effects", String.class)).isEqualTo(seeded.potionEffects());
        assertThat(row.get("held_slot", Integer.class)).isEqualTo(seeded.heldSlot());
        assertThat(row.get("exp_level", Integer.class)).isEqualTo(seeded.expLevel());
        assertThat(row.get("exp_progress", Float.class)).isEqualTo(seeded.expProgress());
        assertThat(row.get("game_mode", String.class)).isEqualTo(seeded.gameMode());
        assertThat(row.get("flying", Integer.class)).isEqualTo((int) seeded.flying());
        assertThat(row.get("vanished_before", Integer.class)).isEqualTo((int) seeded.vanishedBefore());
        assertThat(row.get("allow_flight", Integer.class)).isEqualTo((int) seeded.allowFlight());
        assertThat(row.get("entered_at", Long.class)).isEqualTo(seeded.enteredAt());
    }

    @Test
    void targetingV61LeavesTheTableInItsPreRebuildShapeWithNoServerIdColumn() {
        // Guards the precondition the seed depends on: the helper really stops before the rebuild, so the row is
        // genuinely inserted in the old shape rather than the new one. Selecting server_id at V61 must fail.
        migrateTo(VERSION_BEFORE_REBUILD);

        assertThatCode(() -> dsl.select(DSL.field("server_id"))
                        .from(DSL.table("staff_loadout"))
                        .fetch())
                .as("server_id does not exist before the V62 rebuild")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
    }

    /**
     * A row's worth of pre-V62 {@code staff_loadout} data: the four opaque base64 regions and the captured
     * scalars, in the column set that existed at V61. The values are arbitrary but distinct so a copy that
     * silently swapped or dropped a column would be caught.
     */
    private record OldLoadout(
            String inventory,
            String armor,
            String offhand,
            String potionEffects,
            int heldSlot,
            int expLevel,
            float expProgress,
            String gameMode,
            short flying,
            short vanishedBefore,
            short allowFlight,
            long enteredAt) {

        static OldLoadout sample() {
            return new OldLoadout(
                    "aW52ZW50b3J5",
                    "YXJtb3I=",
                    "b2ZmaGFuZA==",
                    "cG90aW9ucw==",
                    4,
                    30,
                    0.75f,
                    "CREATIVE",
                    (short) 1,
                    (short) 1,
                    (short) 1,
                    1_700_000_000_000L);
        }
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
