package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
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
import com.uxplima.uxmessentials.playerwarps.domain.RatingTally;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link PlayerWarpRecordWriter} against the real V70 player-warp schema on the default embedded SQLite
 * backend, through the same player-warps ports the live plugin uses. It proves each rule of the shared write path from
 * hand-built {@link ImportedPlayerWarp}s: the source name is sanitised into the value-object shape and a global
 * collision from a second owner is renamed, a plaintext password is hashed so it verifies through the password store,
 * the member / whitelist / ban / favourite / rating side lists land in their stores, a re-run of the same import is a
 * no-op, and a dry-run preview writes nothing.
 */
class PlayerWarpRecordWriterTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DAVE = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"), "world");

    private Persistence persistence;
    private PlayerWarpRepository repository;
    private PlayerWarpPasswordStore passwords;
    private WarpMemberStore members;
    private WarpWhitelistStore whitelist;
    private WarpBanStore bans;
    private WarpFavouriteStore favourites;
    private WarpRatingStore ratings;
    private PlayerWarpRecordWriter writer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
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
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void landsAWarpWithEveryScalarFacetSanitisingTheName() {
        RecordOutcome outcome = writer.write(fullShop());

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        PlayerWarp shop = warp("my-base-");
        assertThat(shop.owner().uuid()).isEqualTo(ALICE);
        assertThat(shop.ownerName()).isEqualTo("Alice");
        assertThat(shop.displayName().orElseThrow().value()).isEqualTo("My Base!");
        assertThat(shop.access()).isEqualTo(WarpAccess.PASSWORD);
        assertThat(shop.categoryId()).contains("shops");
        assertThat(shop.description().orElseThrow().value()).isEqualTo("Best deals");
        assertThat(shop.icon().orElseThrow().value()).isEqualTo("DIAMOND_BLOCK");
        assertThat(shop.price().amount()).isEqualByComparingTo(BigDecimal.valueOf(250.0));
        assertThat(shop.visits().count()).isEqualTo(7L);
        assertThat(shop.visits().uniqueVisitors()).isEqualTo(4);
    }

    @Test
    void hashesThePasswordSoItVerifiesThroughTheStore() {
        writer.write(fullShop());
        PlayerWarpId id = warp("my-base-").id().orElseThrow();

        assertThat(passwords.matches(id, "s3cret"))
                .as("the imported password verifies")
                .isTrue();
        assertThat(passwords.matches(id, "wrong"))
                .as("a wrong password does not")
                .isFalse();
    }

    @Test
    void landsTheMemberWhitelistBanFavouriteAndRatingSideLists() {
        writer.write(fullShop());
        PlayerWarpId id = warp("my-base-").id().orElseThrow();

        assertThat(members.roleOf(id, BOB)).contains(WarpRole.CO_OWNER);
        assertThat(whitelist.list(id)).containsExactly(CAROL);
        assertThat(bans.find(id, DAVE)).isPresent();
        assertThat(favourites.contains(BOB, id)).isTrue();
        RatingTally tally = ratings.tally(id);
        assertThat(tally.count()).isEqualTo(2);
        assertThat(tally.sum()).isEqualTo(8L); // 5 + 3
        assertThat(warp("my-base-").ratings().count())
                .as("the rollup is written onto the warp row")
                .isEqualTo(2);
    }

    @Test
    void renamesAGlobalNameCollisionFromASecondOwner() {
        writer.write(simple(ALICE, "Alice", "shop", WarpAccess.PUBLIC));
        RecordOutcome second = writer.write(simple(BOB, "Bob", "shop", WarpAccess.PUBLIC));

        assertThat(second).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(warp("shop").owner().uuid()).isEqualTo(ALICE);
        assertThat(warp("shop2").owner().uuid())
                .as("the second owner's warp is renamed")
                .isEqualTo(BOB);
    }

    @Test
    void aReRunOfTheSameImportIsANoOp() {
        writer.write(fullShop());
        RecordOutcome second = writer.write(fullShop());

        assertThat(second).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(repository.count(new PlayerRef(ALICE, "Alice"))).isEqualTo(1);
        assertThat(whitelist.list(warp("my-base-").id().orElseThrow())).containsExactly(CAROL);
    }

    @Test
    void aDryRunPreviewWritesNothing() {
        assertThat(writer.preview(simple(ALICE, "Alice", "ghost", WarpAccess.PUBLIC)))
                .isEqualTo(RecordOutcome.WRITTEN);
        assertThat(repository.existsByName(PlayerWarpName.of("ghost")))
                .as("preview wrote no row")
                .isFalse();

        writer.write(simple(ALICE, "Alice", "ghost", WarpAccess.PUBLIC));
        assertThat(writer.preview(simple(ALICE, "Alice", "ghost", WarpAccess.PUBLIC)))
                .as("an already-imported warp previews as skipped")
                .isEqualTo(RecordOutcome.SKIPPED);
    }

    private PlayerWarp warp(String name) {
        return repository
                .findByName(PlayerWarpName.of(name))
                .orElseThrow(() -> new AssertionError("no warp named " + name));
    }

    private ImportedPlayerWarp fullShop() {
        Instant now = Instant.ofEpochMilli(1_000L);
        return new ImportedPlayerWarp(
                ALICE,
                "Alice",
                "My Base!",
                Optional.of("My Base!"),
                new Position(WORLD, 12.5, 64.0, -8.25, 90.0f, 12.0f),
                Optional.of("shops"),
                Optional.of("Best deals"),
                Optional.of("DIAMOND_BLOCK"),
                WarpAccess.PASSWORD,
                Optional.of("s3cret"),
                BigDecimal.valueOf(250.0),
                "default",
                BigDecimal.valueOf(99.5),
                now,
                7L,
                4,
                List.of(new ImportedPlayerWarp.Rating(BOB, 5, now), new ImportedPlayerWarp.Rating(CAROL, 3, now)),
                List.of(new ImportedPlayerWarp.Member(BOB, WarpRole.CO_OWNER, now)),
                List.of(CAROL),
                List.of(new ImportedPlayerWarp.Ban(DAVE, Optional.empty(), Optional.empty(), Optional.empty(), now)),
                List.of(BOB));
    }

    private ImportedPlayerWarp simple(UUID owner, String ownerName, String name, WarpAccess access) {
        Instant now = Instant.ofEpochMilli(2_000L);
        return new ImportedPlayerWarp(
                owner,
                ownerName,
                name,
                Optional.empty(),
                new Position(WORLD, 0.0, 70.0, 0.0, 0.0f, 0.0f),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                access,
                Optional.empty(),
                BigDecimal.ZERO,
                "default",
                BigDecimal.ZERO,
                now,
                0L,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
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
