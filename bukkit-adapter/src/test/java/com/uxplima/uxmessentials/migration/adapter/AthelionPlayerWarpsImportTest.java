package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter;
import com.uxplima.uxmessentials.migration.convert.athelion.AthelionPlayerWarpsConvert;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
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
 * The Athelion end-to-end golden-file: it seeds an Athelion {@code data.yml} fixture, drives the real
 * {@link AthelionPlayerWarpsConvert} plan into the shared {@link PlayerWarpRecordWriter}, and asserts the warps land on
 * the new player-warp schema over the default embedded SQLite backend. It proves the whole chain. The source name is
 * sanitised and a global collision from a second owner is renamed, a plaintext password is hashed so it verifies through
 * the password store, the category / bans / rating rollup carry across, a password warp maps to {@code PASSWORD} access,
 * a warp in an unknown world is dropped, a dry-run preview writes nothing, and a second run imports nothing new.
 */
class AthelionPlayerWarpsImportTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"), "world");
    private static final WorldRef NETHER =
            new WorldRef(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000"), "nether");

    private static final String DATA_YML = """
            warps:
              11111111-aaaa-1111-aaaa-111111111111:
                ==: dev.revivalo.playerwarps.warp.Warp
                name: "Alice's Shop"
                display-name: "Alice's Shop"
                owner-id: 11111111-1111-1111-1111-111111111111
                loc:
                  ==: org.bukkit.Location
                  world: world
                  x: 12.5
                  y: 64.0
                  z: -8.25
                  yaw: 90.0
                  pitch: 12.0
                lore: "Best deals in town"
                category: shops
                password: s3cret
                status: PASSWORD_PROTECTED
                admission: 250
                visits: 42
                ratings: 8
                reviewers:
                  - 22222222-2222-2222-2222-222222222222
                  - 33333333-3333-3333-3333-333333333333
                blocked-players:
                  - 33333333-3333-3333-3333-333333333333
                date-created: 1000
              22222222-bbbb-2222-bbbb-222222222222:
                ==: dev.revivalo.playerwarps.warp.Warp
                name: hideout
                display-name: hideout
                owner-id: 22222222-2222-2222-2222-222222222222
                loc:
                  ==: org.bukkit.Location
                  world: nether
                  x: 0.0
                  y: 70.0
                  z: 0.0
                  yaw: 0.0
                  pitch: 0.0
                category: all
                status: CLOSED
                date-created: 2000
              33333333-cccc-3333-cccc-333333333333:
                ==: dev.revivalo.playerwarps.warp.Warp
                name: lost
                display-name: lost
                owner-id: 11111111-1111-1111-1111-111111111111
                loc:
                  ==: org.bukkit.Location
                  world: removed_world
                  x: 0.0
                  y: 70.0
                  z: 0.0
                  yaw: 0.0
                  pitch: 0.0
                status: OPENED
                date-created: 3000
              44444444-dddd-4444-dddd-444444444444:
                ==: dev.revivalo.playerwarps.warp.Warp
                name: "Alice's Shop"
                display-name: "Alice's Shop"
                owner-id: 22222222-2222-2222-2222-222222222222
                loc:
                  ==: org.bukkit.Location
                  world: world
                  x: 1.0
                  y: 65.0
                  z: 2.0
                  yaw: 0.0
                  pitch: 0.0
                status: OPENED
                date-created: 4000
            """;

    private Persistence persistence;
    private PlayerWarpRepository repository;
    private PlayerWarpPasswordStore passwords;
    private WarpBanStore bans;
    private WarpRatingStore ratings;
    private PlayerWarpRecordWriter writer;

    @TempDir
    Path pluginsDir;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), NoopLogger.INSTANCE);
        Clock clock = Clock.systemUTC();
        repository = PlayerWarpRepositories.cached(persistence);
        passwords = PlayerWarpRepositories.passwordStore(persistence);
        bans = PlayerWarpRepositories.banStore(persistence);
        ratings = PlayerWarpRepositories.ratingStore(persistence);
        writer = new PlayerWarpRecordWriter(
                repository,
                passwords,
                PlayerWarpRepositories.memberStore(persistence, NoopLogger.INSTANCE),
                PlayerWarpRepositories.whitelistStore(persistence, clock),
                bans,
                PlayerWarpRepositories.favouriteStore(persistence, clock),
                ratings);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void importsTheResolvableWarpsWithTheirFacetsSideListsAndAHashedPassword() {
        planRecords().forEach(record -> writer.write(record.warp()));

        PlayerWarp shop = passwordShop();
        assertThat(shop.name().value()).startsWith("alice-s-shop");
        assertThat(shop.displayName().orElseThrow().value()).isEqualTo("Alice's Shop");
        assertThat(shop.access()).isEqualTo(WarpAccess.PASSWORD);
        assertThat(shop.categoryId()).contains("shops");
        assertThat(shop.description().orElseThrow().value()).isEqualTo("Best deals in town");
        assertThat(shop.visits().count()).isEqualTo(42L);

        PlayerWarpId shopId = shop.id().orElseThrow();
        assertThat(passwords.matches(shopId, "s3cret"))
                .as("the imported password verifies through the store")
                .isTrue();
        assertThat(passwords.matches(shopId, "wrong")).isFalse();
        assertThat(bans.find(shopId, CAROL))
                .as("the blocked player became a warp ban")
                .isPresent();
        assertThat(ratings.tally(shopId).count()).isEqualTo(2);
        assertThat(ratings.tally(shopId).sum()).isEqualTo(8L); // 4 + 4, spread from Athelion's total

        PlayerWarp hideout = warp("hideout");
        assertThat(hideout.owner().uuid()).isEqualTo(BOB);
        assertThat(hideout.access()).isEqualTo(WarpAccess.PRIVATE);
    }

    @Test
    void renamesTheGlobalNameCollisionFromASecondOwner() {
        planRecords().forEach(record -> writer.write(record.warp()));

        // Both warps sanitise to the same base; the two owners each keep one, under distinct names.
        PlayerWarp aliceShop = passwordShop();
        PlayerWarp bobShop = warpOwnedBy(BOB, "alice-s-shop");
        assertThat(aliceShop.owner().uuid()).isEqualTo(ALICE);
        assertThat(bobShop.access())
                .as("the second owner's copy has no password, so it stays public")
                .isEqualTo(WarpAccess.PUBLIC);
        assertThat(aliceShop.name().value()).isNotEqualTo(bobShop.name().value());
        assertThat(List.of(aliceShop.name().value(), bobShop.name().value()))
                .containsExactlyInAnyOrder("alice-s-shop", "alice-s-shop2");
    }

    @Test
    void dropsTheWarpWhoseWorldTheServerDoesNotKnow() {
        planRecords().forEach(record -> writer.write(record.warp()));

        assertThat(repository.existsByName(PlayerWarpName.of("lost")))
                .as("the warp in an unknown world is skipped")
                .isFalse();
        // Alice's shop, Bob's renamed shop, and hideout land; lost is dropped.
        assertThat(repository.all()).hasSize(3);
    }

    @Test
    void aDryRunPreviewWritesNothing() {
        planRecords()
                .forEach(record -> assertThat(writer.preview(record.warp())).isNotNull());

        assertThat(repository.all()).as("a preview writes no rows").isEmpty();
    }

    @Test
    void aSecondRunImportsNothingNew() {
        planRecords().forEach(record -> writer.write(record.warp()));
        int afterFirst = repository.all().size();

        planRecords().forEach(record -> writer.write(record.warp()));

        assertThat(repository.all()).as("the re-run adds no warps").hasSize(afterFirst);
    }

    private List<ImportRecord.PlayerWarpRecord> planRecords() {
        Path dataFile = writeDataFile();
        WorldNameResolver worlds = name -> switch (name) {
            case "world" -> Optional.of(WORLD);
            case "nether" -> Optional.of(NETHER);
            default -> Optional.empty();
        };
        AthelionPlayerWarpsConvert convert = new AthelionPlayerWarpsConvert(worlds, dataFile, NoopLogger.INSTANCE);
        try (ImportPlan plan = convert.plan(ImportOptions.live(pluginsDir))) {
            return plan.records()
                    .flatMap(record -> record instanceof ImportRecord.PlayerWarpRecord warp
                            ? java.util.stream.Stream.of(warp)
                            : java.util.stream.Stream.<ImportRecord.PlayerWarpRecord>empty())
                    .toList();
        }
    }

    private Path writeDataFile() {
        Path dataFile = pluginsDir.resolve("data.yml");
        try {
            Files.writeString(dataFile, DATA_YML);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException("failed to seed the Athelion data.yml fixture", failure);
        }
        return dataFile;
    }

    /** The imported Alice's-shop warp: the only PASSWORD-access warp, whichever base name the collision walk gave it. */
    private PlayerWarp passwordShop() {
        return repository.all().stream()
                .filter(warp -> warp.access() == WarpAccess.PASSWORD)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no password-gated warp was imported"));
    }

    private PlayerWarp warpOwnedBy(UUID owner, String namePrefix) {
        return repository.all().stream()
                .filter(warp ->
                        warp.owner().uuid().equals(owner) && warp.name().value().startsWith(namePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no warp owned by " + owner + " named " + namePrefix + "*"));
    }

    private PlayerWarp warp(String name) {
        return repository
                .findByName(PlayerWarpName.of(name))
                .orElseThrow(() -> new AssertionError("no warp named " + name));
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
