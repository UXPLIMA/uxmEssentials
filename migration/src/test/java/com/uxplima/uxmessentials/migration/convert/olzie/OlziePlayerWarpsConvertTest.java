package com.uxplima.uxmessentials.migration.convert.olzie;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
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
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * In-process round-trip for the Olzie PlayerWarps JDBC source. It stands up a real SQLite database with the Olzie
 * schema (whose warps table has no id column, so the side tables key on the SQLite {@code rowid}) seeds a small warp
 * network (a password-gated warp with a category / icon / price / description plus ratings, a ban with a reason, a
 * manager, a favourite and repeat visits; a whitelist warp; a locked warp; and a warp in a world the server no longer
 * knows), then drives {@link OlziePlayerWarpsConvert#plan} and asserts the mapped {@link ImportedPlayerWarp}s. This is
 * the golden-file for the mapping: access is resolved from password / whitelist / locked, the {@code cost} collapses to
 * the default currency, the managers become members, the ban keeps its reason, the side rows land on the right warp,
 * and a warp whose world does not resolve is dropped rather than failing the run.
 */
class OlziePlayerWarpsConvertTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final UUID WORLD_UID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
    private static final UUID NETHER_UID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000");

    @TempDir
    Path dataFolder;

    private Path dbFile;

    @BeforeEach
    void seedDatabase() {
        dbFile = dataFolder.resolve("database.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                Statement st = c.createStatement()) {
            for (String ddl : OlzieFixture.schema()) {
                st.execute(ddl);
            }
            for (String dml : OlzieFixture.seed(ALICE, BOB, CAROL)) {
                st.execute(dml);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to seed the Olzie SQLite fixture", failure);
        }
    }

    @Test
    void mapsEveryWarpWhoseWorldResolvesAndDropsTheUnknownWorldWarp() {
        Map<String, ImportedPlayerWarp> byName = warpsByName();

        assertThat(byName).containsOnlyKeys("Alice's Shop", "hideout", "vault");
    }

    @Test
    void thePasswordWarpCarriesEveryScalarFacetAndPasswordAccess() {
        ImportedPlayerWarp shop = warp("Alice's Shop");

        assertThat(shop.owner()).isEqualTo(ALICE);
        // Olzie does not serialise the owner name, so the author line falls back to a placeholder.
        assertThat(shop.ownerName()).isEqualTo("Unknown");
        assertThat(shop.displayName()).contains("Alice's Shop");
        assertThat(shop.access()).isEqualTo(WarpAccess.PASSWORD);
        assertThat(shop.plaintextPassword()).contains("s3cret");
        assertThat(shop.categoryId()).contains("shops");
        assertThat(shop.description()).contains("Best deals in town");
        assertThat(shop.icon()).contains("DIAMOND_BLOCK");
        assertThat(shop.price()).isEqualByComparingTo(BigDecimal.valueOf(250.0));
        // Olzie credits owner earnings instantly rather than escrowing them, so there is nothing to import.
        assertThat(shop.earnedMoney()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(shop.currencyId()).isEqualTo("default");
        assertThat(shop.visits()).isEqualTo(42L);
        // three visit rows by two distinct visitors.
        assertThat(shop.uniqueVisitors()).isEqualTo(2);
        assertThat(shop.location().world().uid()).isEqualTo(WORLD_UID);
    }

    @Test
    void thePasswordWarpCarriesItsRatingsBanManagerAndFavourite() {
        ImportedPlayerWarp shop = warp("Alice's Shop");

        assertThat(shop.ratings())
                .extracting(ImportedPlayerWarp.Rating::player, ImportedPlayerWarp.Rating::stars)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(BOB, 5), org.assertj.core.groups.Tuple.tuple(CAROL, 3));
        // Olzie is the one player-warp source that records a ban reason, so it survives the import.
        assertThat(shop.bans()).extracting(ImportedPlayerWarp.Ban::player).containsExactly(CAROL);
        assertThat(shop.bans().get(0).reason()).contains("griefing");
        assertThat(shop.bans().get(0).until()).isEmpty();
        // A manager becomes a warp member with the manager role.
        assertThat(shop.members())
                .extracting(ImportedPlayerWarp.Member::player, ImportedPlayerWarp.Member::role)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(BOB, WarpRole.MANAGER));
        assertThat(shop.favourites()).containsExactly(BOB);
        assertThat(shop.whitelist()).isEmpty();
    }

    @Test
    void theWhitelistWarpMapsToWhitelistAccessAndCarriesItsWhitelist() {
        ImportedPlayerWarp hideout = warp("hideout");

        assertThat(hideout.owner()).isEqualTo(BOB);
        assertThat(hideout.access()).isEqualTo(WarpAccess.WHITELIST);
        assertThat(hideout.whitelist()).containsExactlyInAnyOrder(ALICE, CAROL);
        assertThat(hideout.categoryId()).isEmpty();
        assertThat(hideout.description()).isEmpty();
        assertThat(hideout.plaintextPassword()).isEmpty();
        assertThat(hideout.location().world().uid()).isEqualTo(NETHER_UID);
    }

    @Test
    void theLockedWarpMapsToPrivateAccess() {
        ImportedPlayerWarp vault = warp("vault");

        assertThat(vault.owner()).isEqualTo(ALICE);
        assertThat(vault.access()).isEqualTo(WarpAccess.PRIVATE);
        assertThat(vault.plaintextPassword()).isEmpty();
    }

    private ImportedPlayerWarp warp(String name) {
        ImportedPlayerWarp mapped = warpsByName().get(name);
        assertThat(mapped).as("mapped warp %s", name).isNotNull();
        return java.util.Objects.requireNonNull(mapped);
    }

    private Map<String, ImportedPlayerWarp> warpsByName() {
        WorldNameResolver worlds = name -> switch (name) {
            case "world" -> Optional.of(new WorldRef(WORLD_UID, "world"));
            case "nether" -> Optional.of(new WorldRef(NETHER_UID, "nether"));
            default -> Optional.empty();
        };
        OlziePlayerWarpsConfig config =
                new OlziePlayerWarpsConfig(Optional.of("jdbc:sqlite:" + dbFile), "", "", Optional.empty());
        OlziePlayerWarpsConvert convert = new OlziePlayerWarpsConvert(config, worlds, NoOpLogger.INSTANCE);
        try (ImportPlan plan = convert.plan(ImportOptions.live(dataFolder))) {
            return plan.records()
                    .flatMap(record -> record instanceof ImportRecord.PlayerWarpRecord warp
                            ? java.util.stream.Stream.of(warp.warp())
                            : java.util.stream.Stream.<ImportedPlayerWarp>empty())
                    .collect(Collectors.toMap(ImportedPlayerWarp::name, Function.identity()));
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

    /** The Olzie schema and seed rows, shared with the bukkit-adapter end-to-end import test. */
    static final class OlzieFixture {

        private OlzieFixture() {}

        static List<String> schema() {
            return List.of(
                    // The warps table deliberately has no id column: Olzie keys the side tables on the SQLite rowid.
                    "CREATE TABLE playerwarps_warps (name TEXT, uuid TEXT, world TEXT, x REAL, y REAL, z REAL, "
                            + "pitch REAL, yaw REAL, description TEXT, visits INTEGER, date INTEGER, icon TEXT, "
                            + "category TEXT, locked INTEGER, cost REAL, password TEXT, last_rent INTEGER, "
                            + "whitelist_enabled INTEGER, sponsor INTEGER, sponsor_cooldown INTEGER, type TEXT, "
                            + "random_sort INTEGER, earned_rate_rewards INTEGER, set_prices TEXT, tags TEXT)",
                    "CREATE TABLE playerwarps_rates (warp_id INTEGER, uuid TEXT, rate INTEGER, description TEXT)",
                    "CREATE TABLE playerwarps_warps_visits (warp_id INTEGER, player_uuid TEXT, amount INTEGER, "
                            + "time INTEGER, boosted INTEGER)",
                    "CREATE TABLE playerwarps_warps_banned (warp_id INTEGER, player_uuid TEXT, time INTEGER, "
                            + "reason TEXT)",
                    "CREATE TABLE playerwarps_warps_whitelisted (warp_id INTEGER, player_uuid TEXT)",
                    "CREATE TABLE playerwarps_warps_managers (warp_id INTEGER, player_uuid TEXT)",
                    "CREATE TABLE playerwarps_players_favourite_warps (player_uuid TEXT, warp_id INTEGER)");
        }

        static List<String> seed(UUID alice, UUID bob, UUID carol) {
            return List.of(
                    // rowid 1: a password-gated warp, fully decorated
                    "INSERT INTO playerwarps_warps(rowid, name, uuid, world, x, y, z, pitch, yaw, description, "
                            + "visits, date, icon, category, locked, cost, password, whitelist_enabled) VALUES "
                            + "(1, 'Alice''s Shop', '" + alice + "', 'world', 12.5, 64.0, -8.25, 12.0, 90.0, "
                            + "'Best deals in town', 42, 1000, 'DIAMOND_BLOCK', 'shops', 0, 250.0, 's3cret', 0)",
                    // rowid 2: a whitelist warp, no decoration
                    "INSERT INTO playerwarps_warps(rowid, name, uuid, world, x, y, z, pitch, yaw, visits, date, "
                            + "locked, cost, whitelist_enabled) VALUES "
                            + "(2, 'hideout', '" + bob + "', 'nether', 0, 70, 0, 0, 0, 3, 2000, 0, 0, 1)",
                    // rowid 3: a locked (private) warp
                    "INSERT INTO playerwarps_warps(rowid, name, uuid, world, x, y, z, pitch, yaw, visits, date, "
                            + "locked, cost, whitelist_enabled) VALUES "
                            + "(3, 'vault', '" + alice + "', 'world', 0, 70, 0, 0, 0, 5, 3000, 1, 0, 0)",
                    // rowid 4: a warp in a world the server does not know -> dropped
                    "INSERT INTO playerwarps_warps(rowid, name, uuid, world, x, y, z, pitch, yaw, visits, date, "
                            + "locked, cost, whitelist_enabled) VALUES "
                            + "(4, 'lost', '" + alice + "', 'removed_world', 0, 70, 0, 0, 0, 1, 4000, 0, 0, 0)",
                    "INSERT INTO playerwarps_rates VALUES (1, '" + bob + "', 5, 'great'), (1, '" + carol
                            + "', 3, 'ok')",
                    "INSERT INTO playerwarps_warps_banned VALUES (1, '" + carol + "', 1700, 'griefing')",
                    "INSERT INTO playerwarps_warps_managers VALUES (1, '" + bob + "')",
                    "INSERT INTO playerwarps_players_favourite_warps VALUES ('" + bob + "', 1)",
                    "INSERT INTO playerwarps_warps_whitelisted VALUES (2, '" + alice + "'), (2, '" + carol + "')",
                    "INSERT INTO playerwarps_warps_visits VALUES (1, '" + bob + "', 2, 1, 0), " + "(1, '" + bob
                            + "', 1, 2, 0), (1, '" + carol + "', 1, 3, 0)");
        }
    }
}
