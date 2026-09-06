package com.uxplima.uxmessentials.migration.convert.athelion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Athelion parse-and-map golden-file. It seeds an Athelion {@code data.yml} fixture. A password-gated warp with a
 * category, ratings and a blocked player; a closed warp in the default category; and a warp in a world the server no
 * longer knows, then drives {@link AthelionPlayerWarpsConvert#plan} and asserts the mapped {@link ImportedPlayerWarp}s.
 * This pins the mapping: a password becomes {@code PASSWORD} access, a {@code CLOSED} warp becomes {@code PRIVATE}, the
 * {@code all} category collapses to none, {@code admission} becomes the price, blocked-players become bans, the rating
 * total is spread across the reviewers, and a warp whose world does not resolve is dropped rather than failing the run.
 */
class AthelionPlayerWarpsConvertTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final UUID WORLD_UID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
    private static final UUID NETHER_UID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000");

    private static final String DATA_YML = """
            warps:
              11111111-aaaa-1111-aaaa-111111111111:
                ==: dev.revivalo.playerwarps.warp.Warp
                uuid: 11111111-aaaa-1111-aaaa-111111111111
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
                uuid: 22222222-bbbb-2222-bbbb-222222222222
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
                admission: 0
                visits: 3
                ratings: 0
                date-created: 2000
              33333333-cccc-3333-cccc-333333333333:
                ==: dev.revivalo.playerwarps.warp.Warp
                uuid: 33333333-cccc-3333-cccc-333333333333
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
            """;

    @TempDir
    Path dataFolder;

    @Test
    void mapsEveryWarpWhoseWorldResolvesAndDropsTheUnknownWorldWarp() {
        Map<String, ImportedPlayerWarp> byName = warpsByName();

        assertThat(byName).containsOnlyKeys("Alice's Shop", "hideout");
    }

    @Test
    void thePasswordWarpCarriesEveryScalarFacetAndPasswordAccess() {
        ImportedPlayerWarp shop = warp("Alice's Shop");

        assertThat(shop.owner()).isEqualTo(ALICE);
        // Athelion does not serialise the owner name, so the author line falls back to a placeholder.
        assertThat(shop.ownerName()).isEqualTo("Unknown");
        assertThat(shop.displayName()).contains("Alice's Shop");
        assertThat(shop.access()).isEqualTo(WarpAccess.PASSWORD);
        assertThat(shop.plaintextPassword()).contains("s3cret");
        assertThat(shop.categoryId()).contains("shops");
        assertThat(shop.description()).contains("Best deals in town");
        assertThat(shop.icon()).isEmpty();
        assertThat(shop.price()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(shop.currencyId()).isEqualTo("default");
        assertThat(shop.earnedMoney()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(shop.visits()).isEqualTo(42L);
        assertThat(shop.uniqueVisitors()).isEqualTo(0);
        assertThat(shop.location().world().uid()).isEqualTo(WORLD_UID);
    }

    @Test
    void thePasswordWarpReconstructsItsRatingsAndBansTheBlockedPlayer() {
        ImportedPlayerWarp shop = warp("Alice's Shop");

        // The star total (8) is spread across the two reviewers, so the count and average survive: 4 + 4.
        assertThat(shop.ratings())
                .extracting(ImportedPlayerWarp.Rating::player, ImportedPlayerWarp.Rating::stars)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(BOB, 4), org.assertj.core.groups.Tuple.tuple(CAROL, 4));
        assertThat(shop.bans()).extracting(ImportedPlayerWarp.Ban::player).containsExactly(CAROL);
        assertThat(shop.bans().get(0).reason()).isEmpty();
        assertThat(shop.bans().get(0).until()).isEmpty();
        // Athelion has no co-owners, whitelist or favourites, so those side lists arrive empty.
        assertThat(shop.members()).isEmpty();
        assertThat(shop.whitelist()).isEmpty();
        assertThat(shop.favourites()).isEmpty();
    }

    @Test
    void theClosedWarpMapsToPrivateAndDropsTheDefaultCategory() {
        ImportedPlayerWarp hideout = warp("hideout");

        assertThat(hideout.owner()).isEqualTo(BOB);
        assertThat(hideout.access()).isEqualTo(WarpAccess.PRIVATE);
        assertThat(hideout.plaintextPassword()).isEmpty();
        // Athelion's "all" bucket means "uncategorised", so it maps to no category.
        assertThat(hideout.categoryId()).isEmpty();
        assertThat(hideout.ratings()).isEmpty();
        assertThat(hideout.location().world().uid()).isEqualTo(NETHER_UID);
    }

    private ImportedPlayerWarp warp(String name) {
        ImportedPlayerWarp mapped = warpsByName().get(name);
        assertThat(mapped).as("mapped warp %s", name).isNotNull();
        return java.util.Objects.requireNonNull(mapped);
    }

    private Map<String, ImportedPlayerWarp> warpsByName() {
        Path dataFile = writeDataFile();
        WorldNameResolver worlds = name -> switch (name) {
            case "world" -> Optional.of(new WorldRef(WORLD_UID, "world"));
            case "nether" -> Optional.of(new WorldRef(NETHER_UID, "nether"));
            default -> Optional.empty();
        };
        AthelionPlayerWarpsConvert convert = new AthelionPlayerWarpsConvert(worlds, dataFile, NoOpLogger.INSTANCE);
        try (ImportPlan plan = convert.plan(ImportOptions.live(dataFolder))) {
            return plan.records()
                    .flatMap(record -> record instanceof ImportRecord.PlayerWarpRecord warp
                            ? java.util.stream.Stream.of(warp.warp())
                            : java.util.stream.Stream.<ImportedPlayerWarp>empty())
                    .collect(Collectors.toMap(ImportedPlayerWarp::name, Function.identity()));
        }
    }

    private Path writeDataFile() {
        Path dataFile = dataFolder.resolve("data.yml");
        try {
            Files.writeString(dataFile, DATA_YML);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException("failed to seed the Athelion data.yml fixture", failure);
        }
        return dataFile;
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
