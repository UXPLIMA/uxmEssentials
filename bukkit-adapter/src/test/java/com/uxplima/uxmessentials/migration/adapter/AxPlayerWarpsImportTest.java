package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter;
import com.uxplima.uxmessentials.migration.convert.ax.AxPlayerWarpsConfig;
import com.uxplima.uxmessentials.migration.convert.ax.AxPlayerWarpsConvert;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The AxPlayerWarps end-to-end golden-file: it seeds an AxPlayerWarps H2 fixture, drives the real
 * {@link AxPlayerWarpsConvert} plan into the shared {@link PlayerWarpRecordWriter}, and asserts the warps land on the
 * new V70 player-warp schema over the default embedded SQLite backend. It proves the whole chain. The source name is
 * sanitised into the value-object shape, the {@code Access} ordinal maps to our access axis, the category / description
 * / icon / price carry across, the ratings / whitelist / blacklist (as bans) / favourites / visit tally land in their
 * stores, a warp in an unknown world is dropped, and a second run of the same import writes nothing new.
 */
class AxPlayerWarpsImportTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"), "world");
    private static final WorldRef NETHER =
            new WorldRef(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000"), "nether");

    private Persistence persistence;
    private PlayerWarpRepository repository;
    private WarpWhitelistStore whitelist;
    private WarpBanStore bans;
    private WarpFavouriteStore favourites;
    private WarpRatingStore ratings;
    private PlayerWarpRecordWriter writer;

    private String axUrl;
    private Connection axKeepAlive;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) throws SQLException {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), NoopLogger.INSTANCE);
        Clock clock = Clock.systemUTC();
        repository = PlayerWarpRepositories.cached(persistence);
        whitelist = PlayerWarpRepositories.whitelistStore(persistence, clock);
        bans = PlayerWarpRepositories.banStore(persistence);
        favourites = PlayerWarpRepositories.favouriteStore(persistence, clock);
        ratings = PlayerWarpRepositories.ratingStore(persistence);
        writer = new PlayerWarpRecordWriter(
                repository,
                PlayerWarpRepositories.passwordStore(persistence),
                PlayerWarpRepositories.memberStore(persistence, NoopLogger.INSTANCE),
                whitelist,
                bans,
                favourites,
                ratings);

        axUrl = "jdbc:h2:mem:ax_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        axKeepAlive = DriverManager.getConnection(axUrl);
        createAxSchema();
        seedAx();
    }

    @AfterEach
    void tearDown() throws SQLException {
        axKeepAlive.close();
        persistence.close();
    }

    @Test
    void importsEveryResolvableWarpOntoTheSchemaWithItsFacetsAndSideLists() {
        runImport();

        PlayerWarp shop = warp("alice-s-shop");
        assertThat(shop.owner().uuid()).isEqualTo(ALICE);
        assertThat(shop.displayName().orElseThrow().value()).isEqualTo("Alice's Shop");
        assertThat(shop.access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(shop.categoryId()).contains("shops");
        assertThat(shop.description().orElseThrow().value()).isEqualTo("Best deals in town");
        assertThat(shop.icon().orElseThrow().value()).isEqualTo("DIAMOND_BLOCK");
        assertThat(shop.visits().count()).isEqualTo(3L);

        PlayerWarpId shopId = shop.id().orElseThrow();
        assertThat(ratings.tally(shopId).count()).isEqualTo(2);
        assertThat(ratings.tally(shopId).sum()).isEqualTo(8L); // 5 + 3
        assertThat(bans.find(shopId, CAROL))
                .as("the blacklist entry became a warp ban")
                .isPresent();
        assertThat(favourites.contains(BOB, shopId)).isTrue();

        PlayerWarp hideout = warp("hideout");
        assertThat(hideout.access()).isEqualTo(WarpAccess.WHITELIST);
        assertThat(whitelist.list(hideout.id().orElseThrow())).containsExactlyInAnyOrder(ALICE, CAROL);
    }

    @Test
    void dropsTheWarpWhoseWorldTheServerDoesNotKnow() {
        runImport();

        assertThat(repository.existsByName(PlayerWarpName.of("lost")))
                .as("the warp in an unknown world is skipped")
                .isFalse();
        assertThat(repository.all()).hasSize(2);
    }

    @Test
    void aSecondRunImportsNothingNew() {
        runImport();
        int afterFirst = repository.all().size();

        runImport();

        assertThat(repository.all()).as("the re-run adds no warps").hasSize(afterFirst);
    }

    private void runImport() {
        WorldNameResolver worlds = name -> switch (name) {
            case "world" -> Optional.of(WORLD);
            case "nether" -> Optional.of(NETHER);
            default -> Optional.empty();
        };
        AxPlayerWarpsConfig config = new AxPlayerWarpsConfig(Optional.of(axUrl), "", "", Optional.empty());
        AxPlayerWarpsConvert convert = new AxPlayerWarpsConvert(config, worlds, NoopLogger.INSTANCE);
        try (ImportPlan plan = convert.plan(ImportOptions.live(Path.of(".")))) {
            plan.records().forEach(record -> {
                if (record instanceof ImportRecord.PlayerWarpRecord playerWarp) {
                    writer.write(playerWarp.warp());
                }
            });
        }
    }

    private PlayerWarp warp(String name) {
        return repository
                .findByName(PlayerWarpName.of(name))
                .orElseThrow(() -> new AssertionError("no warp named " + name));
    }

    private void createAxSchema() throws SQLException {
        try (Statement st = axKeepAlive.createStatement()) {
            st.execute("CREATE TABLE axplayerwarps_players (id INT PRIMARY KEY, uuid VARCHAR(36), name VARCHAR(128))");
            st.execute("CREATE TABLE axplayerwarps_worlds (id INT PRIMARY KEY, world VARCHAR(512))");
            st.execute("CREATE TABLE axplayerwarps_categories (id INT PRIMARY KEY, category VARCHAR(512))");
            st.execute("CREATE TABLE axplayerwarps_materials (id INT PRIMARY KEY, material VARCHAR(512))");
            st.execute("CREATE TABLE axplayerwarps_currencies (id INT PRIMARY KEY, currency VARCHAR(512))");
            st.execute("CREATE TABLE axplayerwarps_warps (id INT PRIMARY KEY, owner_id INT, world_id INT, "
                    + "x DOUBLE, y DOUBLE, z DOUBLE, yaw REAL, pitch REAL, name VARCHAR(1024), description CLOB, "
                    + "category_id INT, icon_id INT, created BIGINT, currency_id INT, price DOUBLE, "
                    + "earned_money DOUBLE, access TINYINT)");
            st.execute("CREATE TABLE axplayerwarps_ratings (id INT PRIMARY KEY, reviewer_id INT, warp_id INT, "
                    + "stars TINYINT, date BIGINT)");
            st.execute("CREATE TABLE axplayerwarps_visits (id INT PRIMARY KEY, visitor_id INT, warp_id INT, "
                    + "date BIGINT)");
            st.execute("CREATE TABLE axplayerwarps_whitelisted (id INT PRIMARY KEY, player_id INT, warp_id INT, "
                    + "date BIGINT)");
            st.execute("CREATE TABLE axplayerwarps_blacklisted (id INT PRIMARY KEY, player_id INT, warp_id INT, "
                    + "date BIGINT)");
            st.execute("CREATE TABLE axplayerwarps_favorites (id INT PRIMARY KEY, player_id INT, warp_id INT, "
                    + "date BIGINT)");
        }
    }

    private void seedAx() throws SQLException {
        try (Statement st = axKeepAlive.createStatement()) {
            st.execute("INSERT INTO axplayerwarps_players VALUES (1, '" + ALICE + "', 'Alice')");
            st.execute("INSERT INTO axplayerwarps_players VALUES (2, '" + BOB + "', 'Bob')");
            st.execute("INSERT INTO axplayerwarps_players VALUES (3, '" + CAROL + "', 'Carol')");
            st.execute("INSERT INTO axplayerwarps_worlds VALUES (10, 'world'), (11, 'nether'), (12, 'removed_world')");
            st.execute("INSERT INTO axplayerwarps_categories VALUES (20, 'shops')");
            st.execute("INSERT INTO axplayerwarps_materials VALUES (30, 'DIAMOND_BLOCK')");
            st.execute("INSERT INTO axplayerwarps_currencies VALUES (40, 'Vault')");
            st.execute("INSERT INTO axplayerwarps_warps VALUES (100, 1, 10, 12.5, 64.0, -8.25, 90.0, 12.0, "
                    + "'Alice''s Shop', 'Best deals in town', 20, 30, 1000, 40, 250.0, 99.5, 0)");
            st.execute("INSERT INTO axplayerwarps_warps VALUES (101, 2, 11, 0.0, 70.0, 0.0, 0.0, 0.0, "
                    + "'hideout', NULL, NULL, NULL, 2000, NULL, 0.0, 0.0, 1)");
            st.execute("INSERT INTO axplayerwarps_warps VALUES (102, 1, 12, 0.0, 70.0, 0.0, 0.0, 0.0, "
                    + "'lost', NULL, NULL, NULL, 3000, NULL, 0.0, 0.0, 0)");
            st.execute("INSERT INTO axplayerwarps_ratings VALUES (200, 2, 100, 5, 1500), (201, 3, 100, 3, 1600)");
            st.execute("INSERT INTO axplayerwarps_blacklisted VALUES (300, 3, 100, 1700)");
            st.execute("INSERT INTO axplayerwarps_favorites VALUES (400, 2, 100, 1800)");
            st.execute("INSERT INTO axplayerwarps_whitelisted VALUES (500, 1, 101, 2100), (501, 3, 101, 2200)");
            st.execute("INSERT INTO axplayerwarps_visits VALUES (600, 2, 100, 1900), (601, 2, 100, 1950), "
                    + "(602, 3, 100, 1980)");
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
