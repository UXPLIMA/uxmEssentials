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
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.olzie.OlziePlayerWarpsConfig;
import com.uxplima.uxmessentials.migration.convert.olzie.OlziePlayerWarpsConvert;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Olzie PlayerWarps end-to-end golden-file, and the last leg of the player-warps rebuild: it seeds an Olzie SQLite
 * fixture, drives the real {@link OlziePlayerWarpsConvert} plan into the shared {@link PlayerWarpRecordWriter}, and
 * asserts the warps land on the new player-warp schema over the default embedded SQLite backend. It proves the whole
 * chain for the richest source. The source name is sanitised into the value-object shape, access is resolved from the
 * password / whitelist / locked flags, the plaintext password is hashed so it verifies, the category / description /
 * icon / price carry across, the ratings / whitelist / managers (as members) / bans (with reason) / favourites land in
 * their stores, a warp in an unknown world is dropped, and a second run writes nothing new.
 */
class OlziePlayerWarpsImportTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"), "world");
    private static final WorldRef NETHER =
            new WorldRef(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000"), "nether");

    private Persistence persistence;
    private PlayerWarpRepository repository;
    private PlayerWarpPasswordStore passwords;
    private WarpMemberStore members;
    private WarpWhitelistStore whitelist;
    private WarpBanStore bans;
    private WarpFavouriteStore favourites;
    private WarpRatingStore ratings;
    private PlayerWarpRecordWriter writer;

    private Path olzieDbFile;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) throws SQLException {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), NoopLogger.INSTANCE);
        Clock clock = Clock.systemUTC();
        repository = PlayerWarpRepositories.cached(persistence);
        passwords = PlayerWarpRepositories.passwordStore(persistence);
        members = PlayerWarpRepositories.memberStore(persistence, NoopLogger.INSTANCE);
        whitelist = PlayerWarpRepositories.whitelistStore(persistence, clock);
        bans = PlayerWarpRepositories.banStore(persistence);
        favourites = PlayerWarpRepositories.favouriteStore(persistence, clock);
        ratings = PlayerWarpRepositories.ratingStore(persistence);
        writer = new PlayerWarpRecordWriter(repository, passwords, members, whitelist, bans, favourites, ratings);

        olzieDbFile = dataFolder.resolve("olzie-source.db");
        seedOlzie();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void importsEveryResolvableWarpOntoTheSchemaWithItsFacetsAndSideLists() {
        runImport();

        PlayerWarp shop = warp("alice-s-shop");
        assertThat(shop.owner().uuid()).isEqualTo(ALICE);
        assertThat(shop.displayName().orElseThrow().value()).isEqualTo("Alice's Shop");
        assertThat(shop.access()).isEqualTo(WarpAccess.PASSWORD);
        assertThat(shop.categoryId()).contains("shops");
        assertThat(shop.description().orElseThrow().value()).isEqualTo("Best deals in town");
        assertThat(shop.icon().orElseThrow().value()).isEqualTo("DIAMOND_BLOCK");
        assertThat(shop.visits().count()).isEqualTo(42L);

        PlayerWarpId shopId = shop.id().orElseThrow();
        assertThat(passwords.matches(shopId, "s3cret"))
                .as("the imported plaintext password verifies through the store")
                .isTrue();
        assertThat(ratings.tally(shopId).count()).isEqualTo(2);
        assertThat(ratings.tally(shopId).sum()).isEqualTo(8L); // 5 + 3
        assertThat(bans.find(shopId, CAROL)).isPresent();
        assertThat(bans.find(shopId, CAROL).orElseThrow().reason())
                .as("Olzie is the one source that keeps a ban reason")
                .contains("griefing");
        assertThat(members.roleOf(shopId, BOB))
                .as("an Olzie manager becomes a warp member with the manager role")
                .contains(WarpRole.MANAGER);
        assertThat(favourites.contains(BOB, shopId)).isTrue();

        PlayerWarp hideout = warp("hideout");
        assertThat(hideout.access()).isEqualTo(WarpAccess.WHITELIST);
        assertThat(whitelist.list(hideout.id().orElseThrow())).containsExactlyInAnyOrder(ALICE, CAROL);

        assertThat(warp("vault").access()).isEqualTo(WarpAccess.PRIVATE);
    }

    @Test
    void dropsTheWarpWhoseWorldTheServerDoesNotKnow() {
        runImport();

        assertThat(repository.existsByName(PlayerWarpName.of("lost")))
                .as("the warp in an unknown world is skipped")
                .isFalse();
        assertThat(repository.all()).hasSize(3);
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
        OlziePlayerWarpsConfig config =
                new OlziePlayerWarpsConfig(Optional.of("jdbc:sqlite:" + olzieDbFile), "", "", Optional.empty());
        OlziePlayerWarpsConvert convert = new OlziePlayerWarpsConvert(config, worlds, NoopLogger.INSTANCE);
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

    private void seedOlzie() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + olzieDbFile);
                Statement st = c.createStatement()) {
            // The warps table has no id column: Olzie keys the side tables on the SQLite rowid.
            st.execute("CREATE TABLE playerwarps_warps (name TEXT, uuid TEXT, world TEXT, x REAL, y REAL, z REAL, "
                    + "pitch REAL, yaw REAL, description TEXT, visits INTEGER, date INTEGER, icon TEXT, "
                    + "category TEXT, locked INTEGER, cost REAL, password TEXT, last_rent INTEGER, "
                    + "whitelist_enabled INTEGER, sponsor INTEGER, sponsor_cooldown INTEGER, type TEXT, "
                    + "random_sort INTEGER, earned_rate_rewards INTEGER, set_prices TEXT, tags TEXT)");
            st.execute("CREATE TABLE playerwarps_rates (warp_id INTEGER, uuid TEXT, rate INTEGER, description TEXT)");
            st.execute("CREATE TABLE playerwarps_warps_visits (warp_id INTEGER, player_uuid TEXT, amount INTEGER, "
                    + "time INTEGER, boosted INTEGER)");
            st.execute("CREATE TABLE playerwarps_warps_banned (warp_id INTEGER, player_uuid TEXT, time INTEGER, "
                    + "reason TEXT)");
            st.execute("CREATE TABLE playerwarps_warps_whitelisted (warp_id INTEGER, player_uuid TEXT)");
            st.execute("CREATE TABLE playerwarps_warps_managers (warp_id INTEGER, player_uuid TEXT)");
            st.execute("CREATE TABLE playerwarps_players_favourite_warps (player_uuid TEXT, warp_id INTEGER)");
            st.execute("INSERT INTO playerwarps_warps(rowid, name, uuid, world, x, y, z, pitch, yaw, description, "
                    + "visits, date, icon, category, locked, cost, password, whitelist_enabled) VALUES "
                    + "(1, 'Alice''s Shop', '" + ALICE + "', 'world', 12.5, 64.0, -8.25, 12.0, 90.0, "
                    + "'Best deals in town', 42, 1000, 'DIAMOND_BLOCK', 'shops', 0, 250.0, 's3cret', 0)");
            st.execute("INSERT INTO playerwarps_warps(rowid, name, uuid, world, x, y, z, pitch, yaw, visits, date, "
                    + "locked, cost, whitelist_enabled) VALUES "
                    + "(2, 'hideout', '" + BOB + "', 'nether', 0, 70, 0, 0, 0, 3, 2000, 0, 0, 1)");
            st.execute("INSERT INTO playerwarps_warps(rowid, name, uuid, world, x, y, z, pitch, yaw, visits, date, "
                    + "locked, cost, whitelist_enabled) VALUES "
                    + "(3, 'vault', '" + ALICE + "', 'world', 0, 70, 0, 0, 0, 5, 3000, 1, 0, 0)");
            st.execute("INSERT INTO playerwarps_warps(rowid, name, uuid, world, x, y, z, pitch, yaw, visits, date, "
                    + "locked, cost, whitelist_enabled) VALUES "
                    + "(4, 'lost', '" + ALICE + "', 'removed_world', 0, 70, 0, 0, 0, 1, 4000, 0, 0, 0)");
            st.execute("INSERT INTO playerwarps_rates VALUES (1, '" + BOB + "', 5, 'great'), " + "(1, '" + CAROL
                    + "', 3, 'ok')");
            st.execute("INSERT INTO playerwarps_warps_banned VALUES (1, '" + CAROL + "', 1700, 'griefing')");
            st.execute("INSERT INTO playerwarps_warps_managers VALUES (1, '" + BOB + "')");
            st.execute("INSERT INTO playerwarps_players_favourite_warps VALUES ('" + BOB + "', 1)");
            st.execute("INSERT INTO playerwarps_warps_whitelisted VALUES (2, '" + ALICE + "'), (2, '" + CAROL + "')");
            st.execute("INSERT INTO playerwarps_warps_visits VALUES (1, '" + BOB + "', 2, 1, 0), " + "(1, '" + BOB
                    + "', 1, 2, 0), (1, '" + CAROL + "', 1, 3, 0)");
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
