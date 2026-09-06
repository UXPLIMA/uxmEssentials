package com.uxplima.uxmessentials.migration.convert.ax;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * In-process round-trip for the AxPlayerWarps JDBC source. It stands up an H2 database with the AxPlayerWarps schema,
 * seeds a small warp network. Two owners, a public warp with a category / icon / price / description plus ratings,
 * a blacklist, a favourite and repeat visits, a whitelist-access warp, and a warp in a world the server no longer
 * knows, then drives {@link AxPlayerWarpsConvert#plan} and asserts the mapped {@link ImportedPlayerWarp}s. This is the
 * golden-file for the mapping: the {@code Access} ordinal becomes our access axis, the per-warp currency collapses to
 * the default, the source name is kept as the display label, the side tables land on the right warp, and a warp whose
 * world does not resolve is dropped rather than failing the run.
 */
class AxPlayerWarpsConvertTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final UUID WORLD_UID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
    private static final UUID NETHER_UID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000");

    private String url;
    private Connection keepAlive;

    @BeforeEach
    void setUp() throws SQLException {
        url = "jdbc:h2:mem:ax_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        keepAlive = DriverManager.getConnection(url);
        createSchema();
        seed();
    }

    @AfterEach
    void tearDown() throws SQLException {
        keepAlive.close();
    }

    @Test
    void mapsEveryWarpWhoseWorldResolvesAndDropsTheUnknownWorldWarp() {
        Map<String, ImportedPlayerWarp> byName = warpsByName();

        assertThat(byName).containsOnlyKeys("Alice's Shop", "hideout");
    }

    @Test
    void thePublicWarpCarriesEveryScalarFacetAndItsCurrencyCollapsesToTheDefault() {
        ImportedPlayerWarp shop = warp("Alice's Shop");

        assertThat(shop.owner()).isEqualTo(ALICE);
        assertThat(shop.ownerName()).isEqualTo("Alice");
        // The raw source name is kept as the display label, so its original casing survives the id sanitising.
        assertThat(shop.displayName()).contains("Alice's Shop");
        assertThat(shop.access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(shop.categoryId()).contains("shops");
        assertThat(shop.description()).contains("Best deals in town");
        assertThat(shop.icon()).contains("DIAMOND_BLOCK");
        assertThat(shop.price()).isEqualByComparingTo(BigDecimal.valueOf(250.0));
        assertThat(shop.earnedMoney()).isEqualByComparingTo(BigDecimal.valueOf(99.5));
        // AxPlayerWarps stores a currency hook name (Vault); imported prices land in the server default currency.
        assertThat(shop.currencyId()).isEqualTo("default");
        assertThat(shop.plaintextPassword()).isEmpty();
        assertThat(shop.location().world().uid()).isEqualTo(WORLD_UID);
    }

    @Test
    void thePublicWarpCarriesItsRatingsBlacklistFavouritesAndVisitTally() {
        ImportedPlayerWarp shop = warp("Alice's Shop");

        assertThat(shop.ratings())
                .extracting(ImportedPlayerWarp.Rating::player, ImportedPlayerWarp.Rating::stars)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(BOB, 5), org.assertj.core.groups.Tuple.tuple(CAROL, 3));
        // The blacklist maps to warp bans; AxPlayerWarps records no reason or expiry, so those are absent.
        assertThat(shop.bans()).extracting(ImportedPlayerWarp.Ban::player).containsExactly(CAROL);
        assertThat(shop.bans().get(0).reason()).isEmpty();
        assertThat(shop.bans().get(0).until()).isEmpty();
        assertThat(shop.favourites()).containsExactly(BOB);
        assertThat(shop.whitelist()).isEmpty();
        // three visit rows by two distinct visitors.
        assertThat(shop.visits()).isEqualTo(3L);
        assertThat(shop.uniqueVisitors()).isEqualTo(2);
    }

    @Test
    void theWhitelistedWarpMapsToWhitelistAccessAndCarriesItsWhitelist() {
        ImportedPlayerWarp hideout = warp("hideout");

        assertThat(hideout.owner()).isEqualTo(BOB);
        assertThat(hideout.access()).isEqualTo(WarpAccess.WHITELIST);
        assertThat(hideout.whitelist()).containsExactlyInAnyOrder(ALICE, CAROL);
        assertThat(hideout.categoryId()).isEmpty();
        assertThat(hideout.description()).isEmpty();
        assertThat(hideout.icon()).isEmpty();
        assertThat(hideout.location().world().uid()).isEqualTo(NETHER_UID);
    }

    private ImportedPlayerWarp warp(String name) {
        ImportedPlayerWarp mapped = warpsByName().get(name);
        assertThat(mapped).as("mapped warp %s", name).isNotNull();
        return java.util.Objects.requireNonNull(mapped);
    }

    private Map<String, ImportedPlayerWarp> warpsByName() {
        return plan().stream()
                .filter(record -> record instanceof ImportRecord.PlayerWarpRecord)
                .map(record -> ((ImportRecord.PlayerWarpRecord) record).warp())
                .collect(Collectors.toMap(ImportedPlayerWarp::name, Function.identity()));
    }

    private List<ImportRecord> plan() {
        WorldNameResolver worlds = name -> switch (name) {
            case "world" -> Optional.of(new WorldRef(WORLD_UID, "world"));
            case "nether" -> Optional.of(new WorldRef(NETHER_UID, "nether"));
            default -> Optional.empty();
        };
        AxPlayerWarpsConfig config = new AxPlayerWarpsConfig(Optional.of(url), "", "", Optional.empty());
        AxPlayerWarpsConvert convert = new AxPlayerWarpsConvert(config, worlds, NoOpLogger.INSTANCE);
        try (ImportPlan plan = convert.plan(ImportOptions.live(java.nio.file.Path.of(".")))) {
            return plan.records().toList();
        }
    }

    private void createSchema() throws SQLException {
        try (Statement st = keepAlive.createStatement()) {
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

    private void seed() throws SQLException {
        try (Statement st = keepAlive.createStatement()) {
            st.execute("INSERT INTO axplayerwarps_players VALUES (1, '" + ALICE + "', 'Alice')");
            st.execute("INSERT INTO axplayerwarps_players VALUES (2, '" + BOB + "', 'Bob')");
            st.execute("INSERT INTO axplayerwarps_players VALUES (3, '" + CAROL + "', 'Carol')");
            st.execute("INSERT INTO axplayerwarps_worlds VALUES (10, 'world'), (11, 'nether'), (12, 'removed_world')");
            st.execute("INSERT INTO axplayerwarps_categories VALUES (20, 'shops')");
            st.execute("INSERT INTO axplayerwarps_materials VALUES (30, 'DIAMOND_BLOCK')");
            st.execute("INSERT INTO axplayerwarps_currencies VALUES (40, 'Vault')");
            // a public warp, fully decorated
            st.execute("INSERT INTO axplayerwarps_warps VALUES (100, 1, 10, 12.5, 64.0, -8.25, 90.0, 12.0, "
                    + "'Alice''s Shop', 'Best deals in town', 20, 30, 1000, 40, 250.0, 99.5, 0)");
            // a whitelist-access warp, no decoration
            st.execute("INSERT INTO axplayerwarps_warps VALUES (101, 2, 11, 0.0, 70.0, 0.0, 0.0, 0.0, "
                    + "'hideout', NULL, NULL, NULL, 2000, NULL, 0.0, 0.0, 1)");
            // a warp in a world the server does not know -> dropped
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

    private enum NoOpLogger implements Logger {
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
