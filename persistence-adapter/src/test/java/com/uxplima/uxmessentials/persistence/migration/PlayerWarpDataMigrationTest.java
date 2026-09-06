package com.uxplima.uxmessentials.persistence.migration;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatings.PLAYER_WARP_RATINGS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatingsV1Legacy.PLAYER_WARP_RATINGS_V1_LEGACY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpsV1Legacy.PLAYER_WARPS_V1_LEGACY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.persistence.playerwarps.JooqPlayerWarpRepository;
import com.uxplima.uxmessentials.persistence.playerwarps.Pbkdf2PasswordHasher;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpDataMigration;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRenameNotice;
import com.uxplima.uxmessentials.persistence.runtime.DataSourceFactory;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseSettings;
import com.uxplima.uxmessentials.playerwarps.application.port.PasswordHasher;
import com.uxplima.uxmessentials.playerwarps.domain.PasswordHash;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link PlayerWarpDataMigration} against the real V70 schema on the default embedded SQLite backend. It
 * migrates a fresh database to head so the {@code _v1_legacy} tables exist, seeds them the way the shipped plugin
 * would have left them, runs the one-shot migration, and proves each rule from the migration contract: the
 * globally-unique name collision is resolved deterministically, a plaintext password is hashed and never stored in
 * the clear, {@code access} is derived from the legacy flags, ratings are rehomed onto the surrogate id with clamped
 * stars and a recomputed summary, exactly one rename notice is emitted, and the run is idempotent, the legacy
 * tables are dropped and a second run is a no-op. A dedicated case seeds names the old schema allowed but the V70
 * {@link PlayerWarpName} rejects (too short, an internal space, over length) and proves every migrated row still
 * reads back through {@link JooqPlayerWarpRepository}. The read path that would throw on an unsanitised name and
 * break the global browse for everyone.
 *
 * <p>Infrastructure mirrors {@code PlayerWarpsRebuildMigrationTest}: the production
 * {@link DataSourceFactory}/{@link DatabaseSettings} build the single-writer SQLite pool over a temp-folder database.
 * Seeds are wrapped in a transaction because that pool runs with autoCommit off, so an insert left outside one is
 * rolled back when the lone connection returns to the pool.
 */
class PlayerWarpDataMigrationTest {

    private static final String WORLD_UID = "00000000-0000-0000-0000-000000000000";
    private static final String PASSWORD = "sifre123";

    private static final UUID OWNER_KEEPS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_RENAMED = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OWNER_LOCKED = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_DEFAULT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OWNER_NONCONFORMING = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID RATER_ONE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID RATER_TWO = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID RATER_ORPHAN = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private final PasswordHasher hasher = new Pbkdf2PasswordHasher();
    private final List<PlayerWarpRenameNotice> notices = new ArrayList<>();

    private HikariDataSource dataSource;
    private DSLContext dsl;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        dataSource = DataSourceFactory.create(new DatabaseSettings(new SqliteConfig(), dataFolder));
        dsl = DSL.using(dataSource, SQLDialect.SQLITE);
        migrateToHead();
        seedLegacyData();
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void theFirstClaimantKeepsTheNameAndTheLaterDuplicateIsRenamed() {
        runMigration();

        PlayerWarpsRecord kept = warpByName("dukkan");
        PlayerWarpsRecord renamed = warpByName("dukkan2");
        assertThat(kept).as("the earlier-created dukkan keeps the name").isNotNull();
        assertThat(renamed).as("the later dukkan becomes dukkan2").isNotNull();
        assertThat(kept.getOwner()).isEqualTo(OWNER_KEEPS.toString());
        assertThat(renamed.getOwner()).isEqualTo(OWNER_RENAMED.toString());
        assertThat(kept.getId())
                .as("the two warps carry distinct surrogate ids")
                .isNotEqualTo(renamed.getId());
    }

    @Test
    void aLegacyPasswordIsHashedVerifiableAndNeverStoredInTheClear() {
        runMigration();

        PlayerWarpsRecord renamed = warpByName("dukkan2");
        assertThat(renamed.getAccess()).isEqualTo("PASSWORD");
        assertThat(renamed.getPasswordAlgorithm()).isNotBlank();
        PasswordHash stored =
                new PasswordHash(renamed.getPasswordAlgorithm(), renamed.getPasswordSalt(), renamed.getPasswordHash());
        assertThat(hasher.verify(PASSWORD, stored))
                .as("the migrated digest verifies against the original plaintext")
                .isTrue();
        assertThat(renamed.intoMap().values())
                .as("the plaintext password appears nowhere in the migrated row")
                .noneMatch(value -> value instanceof String text && text.contains(PASSWORD));
    }

    @Test
    void accessIsDerivedFromTheLegacyPublicLockedAndPasswordFlags() {
        runMigration();

        assertThat(warpByName("dukkan").getAccess())
                .as("public flag maps to PUBLIC")
                .isEqualTo("PUBLIC");
        assertThat(warpByName("gizli").getAccess())
                .as("locked maps to owner-only PRIVATE")
                .isEqualTo("PRIVATE");
        assertThat(warpByName("kapali").getAccess())
                .as("neither flag set falls back to PRIVATE")
                .isEqualTo("PRIVATE");
        assertThat(warpByName("dukkan2").getAccess())
                .as("a set password wins over public")
                .isEqualTo("PASSWORD");
    }

    @Test
    void ratingsAreRehomedOntoTheSurrogateIdWithClampedStarsAndARecomputedSummary() {
        runMigration();

        PlayerWarpsRecord renamed = warpByName("dukkan2");
        List<Integer> stars = dsl.select(PLAYER_WARP_RATINGS.STARS)
                .from(PLAYER_WARP_RATINGS)
                .where(PLAYER_WARP_RATINGS.WARP_ID.eq(renamed.getId()))
                .orderBy(PLAYER_WARP_RATINGS.STARS.asc())
                .fetch(PLAYER_WARP_RATINGS.STARS);
        assertThat(stars)
                .as("both ratings landed on the renamed warp, the 9.0 clamped to 5")
                .containsExactly(4, 5);
        assertThat(dsl.selectCount().from(PLAYER_WARP_RATINGS).fetchOne(0, int.class))
                .as("the orphan rating was skipped, not inserted")
                .isEqualTo(2);

        Long ratedAt = dsl.select(PLAYER_WARP_RATINGS.RATED_AT)
                .from(PLAYER_WARP_RATINGS)
                .where(PLAYER_WARP_RATINGS.WARP_ID.eq(renamed.getId()))
                .limit(1)
                .fetchOne(PLAYER_WARP_RATINGS.RATED_AT);
        assertThat(ratedAt).as("rated_at seeds from the warp's creation time").isEqualTo(2_000L);
        assertThat(renamed.getRatingCount()).isEqualTo(2);
        assertThat(renamed.getRatingSum()).isEqualTo(9L);
        assertThat(renamed.getRatingAverage()).isEqualTo(4.5);
        assertThat(renamed.getRatingScore()).isEqualTo(4.5);
        assertThat(warpByName("dukkan").getRatingCount())
                .as("a warp with no ratings stays at zero")
                .isEqualTo(0);
    }

    @Test
    void exactlyOneRenameNoticeIsEmittedForTheRenamedWarp() {
        runMigration();

        assertThat(notices).hasSize(1);
        PlayerWarpRenameNotice notice = notices.getFirst();
        assertThat(notice.owner()).isEqualTo(OWNER_RENAMED);
        assertThat(notice.oldName()).isEqualTo("dukkan");
        assertThat(notice.newName()).isEqualTo("dukkan2");
    }

    @Test
    void theWelcomeMessageIsDroppedAndNotCarriedOntoTheNewRow() {
        runMigration();

        assertThat(warpByName("dukkan").intoMap().values())
                .as("the welcome-message blob is never carried across")
                .noneMatch(value -> value instanceof String text && text.contains("Hos geldin"));
    }

    @Test
    void theLegacyTableIsDroppedAndASecondRunIsANoOp() {
        runMigration();

        assertThatCode(() -> dsl.select(PLAYER_WARPS_V1_LEGACY.OWNER)
                        .from(PLAYER_WARPS_V1_LEGACY)
                        .fetch())
                .as("the legacy table is gone after the migration")
                .isInstanceOf(org.jooq.exception.DataAccessException.class);

        int warpsAfterFirst = dsl.selectCount().from(PLAYER_WARPS).fetchOne(0, int.class);
        int ratingsAfterFirst = dsl.selectCount().from(PLAYER_WARP_RATINGS).fetchOne(0, int.class);
        notices.clear();

        assertThatCode(this::runMigration)
                .as("a second run finds no legacy table and no-ops")
                .doesNotThrowAnyException();
        assertThat(dsl.selectCount().from(PLAYER_WARPS).fetchOne(0, int.class))
                .as("the second run inserts no duplicate warps")
                .isEqualTo(warpsAfterFirst);
        assertThat(dsl.selectCount().from(PLAYER_WARP_RATINGS).fetchOne(0, int.class))
                .as("the second run inserts no duplicate ratings")
                .isEqualTo(ratingsAfterFirst);
        assertThat(notices).as("the second run emits no rename notices").isEmpty();
    }

    @Test
    void nonConformingLegacyNamesAreSanitisedAndEveryMigratedRowStillReadsThroughTheRepository() {
        String longName = "a".repeat(40);
        // The accented letter and the bang both fall outside the new charset, so this legacy name folds to "caf--".
        String unicodeName = "café!";
        seedWarp(OWNER_NONCONFORMING, "ab", 3_000L, 1, 0, null, null, 0L);
        seedWarp(OWNER_NONCONFORMING, "cool base", 3_100L, 1, 0, null, null, 0L);
        seedWarp(OWNER_NONCONFORMING, longName, 3_200L, 1, 0, null, null, 0L);
        seedWarp(OWNER_NONCONFORMING, unicodeName, 3_300L, 1, 0, null, null, 0L);

        runMigration();

        assertThat(warpByName("ab-").getOwner())
                .as("the two-character name is padded up to the minimum length")
                .isEqualTo(OWNER_NONCONFORMING.toString());
        assertThat(warpByName("cool-base"))
                .as("the internal space is folded to a dash")
                .isNotNull();
        assertThat(warpByName("a".repeat(32)))
                .as("the 40-character name is capped to 32")
                .isNotNull();
        assertThat(warpByName("caf--"))
                .as("unicode and punctuation fold to dashes")
                .isNotNull();

        JooqPlayerWarpRepository repository = new JooqPlayerWarpRepository(dsl);
        assertThatCode(repository::all)
                .as("one unsanitised row would throw here on PlayerWarpName.of and break the browse for everyone")
                .doesNotThrowAnyException();
        assertThat(repository.all())
                .as("every migrated warp maps back to the domain")
                .hasSize(8);
        assertThat(repository.ownedBy(new PlayerRef(OWNER_NONCONFORMING, OWNER_NONCONFORMING.toString())))
                .as("all four coerced warps read back for their owner")
                .hasSize(4);
        assertThat(repository.findByName(PlayerWarpName.of("cool-base")))
                .as("the sanitised name is addressable by lookup")
                .isPresent();

        assertThat(notices)
                .as("every coerced name emits a rename notice carrying its original legacy name")
                .contains(
                        new PlayerWarpRenameNotice(OWNER_NONCONFORMING, "ab", "ab-"),
                        new PlayerWarpRenameNotice(OWNER_NONCONFORMING, "cool base", "cool-base"),
                        new PlayerWarpRenameNotice(OWNER_NONCONFORMING, longName, "a".repeat(32)),
                        new PlayerWarpRenameNotice(OWNER_NONCONFORMING, unicodeName, "caf--"));
    }

    private void runMigration() {
        new PlayerWarpDataMigration(dsl, hasher, notices::add, NoopLogger.INSTANCE).run();
    }

    private PlayerWarpsRecord warpByName(String name) {
        PlayerWarpsRecord record =
                dsl.selectFrom(PLAYER_WARPS).where(PLAYER_WARPS.NAME.eq(name)).fetchOne();
        assertThat(record).as("player_warps row named %s", name).isNotNull();
        return record;
    }

    private void seedLegacyData() {
        seedWarp(OWNER_KEEPS, "dukkan", 1_000L, 1, 0, null, "Hos geldin", 7L);
        seedWarp(OWNER_RENAMED, "dukkan", 2_000L, 1, 0, PASSWORD, null, 3L);
        seedWarp(OWNER_LOCKED, "gizli", 1_500L, 0, 1, null, "", 0L);
        seedWarp(OWNER_DEFAULT, "kapali", 1_800L, 0, 0, null, null, 0L);
        seedRating(OWNER_RENAMED, "dukkan", RATER_ONE, 4.0);
        seedRating(OWNER_RENAMED, "dukkan", RATER_TWO, 9.0);
        seedRating(OWNER_KEEPS, "yok", RATER_ORPHAN, 3.0);
    }

    private void seedWarp(
            UUID owner,
            String name,
            long createdAt,
            int isPublic,
            int isLocked,
            @Nullable String password,
            @Nullable String welcome,
            long visitors) {
        dsl.transaction(configuration -> DSL.using(configuration)
                .insertInto(PLAYER_WARPS_V1_LEGACY)
                .set(PLAYER_WARPS_V1_LEGACY.OWNER, owner.toString())
                .set(PLAYER_WARPS_V1_LEGACY.NAME, name)
                .set(PLAYER_WARPS_V1_LEGACY.WORLD, WORLD_UID)
                .set(PLAYER_WARPS_V1_LEGACY.WORLD_NAME, "world")
                .set(PLAYER_WARPS_V1_LEGACY.X, 1.0)
                .set(PLAYER_WARPS_V1_LEGACY.Y, 64.0)
                .set(PLAYER_WARPS_V1_LEGACY.Z, 2.0)
                .set(PLAYER_WARPS_V1_LEGACY.YAW, 0.0f)
                .set(PLAYER_WARPS_V1_LEGACY.PITCH, 0.0f)
                .set(PLAYER_WARPS_V1_LEGACY.IS_PUBLIC, isPublic)
                .set(PLAYER_WARPS_V1_LEGACY.CREATED_AT, createdAt)
                .set(PLAYER_WARPS_V1_LEGACY.VISITORS, visitors)
                .set(PLAYER_WARPS_V1_LEGACY.IS_LOCKED, isLocked)
                .set(PLAYER_WARPS_V1_LEGACY.PASSWORD, password)
                .set(PLAYER_WARPS_V1_LEGACY.WELCOME_MESSAGE, welcome)
                .execute());
    }

    private void seedRating(UUID owner, String warpName, UUID player, double rating) {
        dsl.transaction(configuration -> DSL.using(configuration)
                .insertInto(PLAYER_WARP_RATINGS_V1_LEGACY)
                .set(PLAYER_WARP_RATINGS_V1_LEGACY.OWNER_UUID, owner.toString())
                .set(PLAYER_WARP_RATINGS_V1_LEGACY.WARP_NAME, warpName)
                .set(PLAYER_WARP_RATINGS_V1_LEGACY.PLAYER_UUID, player.toString())
                .set(PLAYER_WARP_RATINGS_V1_LEGACY.RATING, rating)
                .execute());
    }

    private void migrateToHead() {
        Flyway.configure(getClass().getClassLoader())
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
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

    /** Swallows the migration's operator diagnostics so the test output stays quiet. */
    private enum NoopLogger implements Logger {
        INSTANCE;

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
