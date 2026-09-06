package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpFavourites.PLAYER_WARP_FAVOURITES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatings.PLAYER_WARP_RATINGS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.Sponsorship;
import com.uxplima.uxmessentials.playerwarps.domain.VisitSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEffects;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqPlayerWarpRepository} against the default embedded SQLite backend with the
 * Flyway V1-V70 migrations applied. It proves the two round-trip shapes the rebuild has to get right, a
 * fully-populated aggregate with every optional present and a bare one with every optional empty, plus the
 * surrogate-id upsert (insert assigns and returns an id, a second save under that id updates in place), the
 * security invariant that an update never wipes a set password, the app-managed side-table cascade on delete, the
 * atomic visit increment, and the owner-scoped queries.
 */
class JooqPlayerWarpRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final PlayerRef OTHER = new PlayerRef(UUID.randomUUID(), "Other");
    private static final Position POSITION = new Position(WORLD, 12.5, 64.0, -30.5, 90.0f, -12.0f);

    private Persistence persistence;
    private JooqPlayerWarpRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repo = new JooqPlayerWarpRepository(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void aFullyPopulatedWarpRoundTripsFieldForField() {
        PlayerWarp full = fullWarp();

        PlayerWarpId id = repo.save(full);
        PlayerWarp expected = full.withId(id);

        assertThat(repo.findById(id)).hasValueSatisfying(loaded -> assertMatches(loaded, expected));
        assertThat(repo.findByName(full.name())).hasValueSatisfying(loaded -> assertMatches(loaded, expected));
    }

    @Test
    void anAllOptionalsEmptyWarpRoundTripsFieldForField() {
        PlayerWarp empty =
                PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("hut"), POSITION, Instant.ofEpochMilli(3_000L));

        PlayerWarpId id = repo.save(empty);

        assertThat(repo.findById(id)).hasValueSatisfying(loaded -> assertMatches(loaded, empty.withId(id)));
    }

    @Test
    void saveAssignsAnIdOnInsertAndUpsertsUnderThatIdOnUpdate() {
        PlayerWarpId first = repo.save(fullWarp());
        assertThat(first.value()).isEqualTo(1L);

        PlayerWarp reloaded = repo.findById(first).orElseThrow();
        PlayerWarpId again = repo.save(reloaded.withAccess(WarpAccess.PUBLIC, Instant.ofEpochMilli(50_000L)));

        assertThat(again).isEqualTo(first);
        assertThat(repo.count(OWNER)).isEqualTo(1); // upsert, not a second row
        assertThat(repo.findById(first).orElseThrow().access()).isEqualTo(WarpAccess.PUBLIC);
    }

    @Test
    void anUpdateNeverWipesAPreviouslySetPassword() {
        PlayerWarpId id = repo.save(fullWarp());
        // A password write is a separate concern (P4); seed the columns directly as that path would. The pool runs
        // with autoCommit off, so the seed has to go through a committed transaction to be visible to later reads.
        persistence
                .dsl()
                .transaction(configuration -> DSL.using(configuration)
                        .update(PLAYER_WARPS)
                        .set(PLAYER_WARPS.PASSWORD_ALGORITHM, "PBKDF2WithHmacSHA256")
                        .set(PLAYER_WARPS.PASSWORD_SALT, "the-salt")
                        .set(PLAYER_WARPS.PASSWORD_HASH, "the-hash")
                        .where(PLAYER_WARPS.ID.eq(id.value()))
                        .execute());

        PlayerWarp reloaded = repo.findById(id).orElseThrow();
        assertThat(reloaded.passwordSet()).isTrue();
        repo.save(reloaded.withAccess(WarpAccess.PUBLIC, Instant.ofEpochMilli(60_000L)));

        PlayerWarpsRecord row = persistence
                .dsl()
                .selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .fetchOne();
        assertThat(row).isNotNull();
        assertThat(row.getPasswordAlgorithm()).isEqualTo("PBKDF2WithHmacSHA256");
        assertThat(row.getPasswordHash()).isEqualTo("the-hash");
        assertThat(repo.findById(id).orElseThrow().passwordSet()).isTrue();
    }

    @Test
    void deleteByIdRemovesTheWarpAndItsSeededSideRows() {
        PlayerWarpId id = repo.save(fullWarp());
        // Commit the seed (the pool runs with autoCommit off) so the delete has a real side row to cascade over.
        persistence
                .dsl()
                .transaction(configuration -> DSL.using(configuration)
                        .insertInto(PLAYER_WARP_RATINGS)
                        .set(PLAYER_WARP_RATINGS.WARP_ID, id.value())
                        .set(PLAYER_WARP_RATINGS.PLAYER_UUID, UUID.randomUUID().toString())
                        .set(PLAYER_WARP_RATINGS.STARS, 5)
                        .set(PLAYER_WARP_RATINGS.RATED_AT, 1L)
                        .execute());
        assertThat(persistence.dsl().fetchCount(PLAYER_WARP_RATINGS, PLAYER_WARP_RATINGS.WARP_ID.eq(id.value())))
                .as("the seed rating row is present before the delete")
                .isOne();

        repo.deleteById(id);

        assertThat(repo.findById(id)).isEmpty();
        assertThat(persistence.dsl().fetchCount(PLAYER_WARP_RATINGS, PLAYER_WARP_RATINGS.WARP_ID.eq(id.value())))
                .as("the app-managed cascade removed the seeded rating row too")
                .isZero();
    }

    @Test
    void recordVisitIncrementsOnlyTheVisitCount() {
        PlayerWarp seed = fullWarp();
        PlayerWarpId id = repo.save(seed);

        repo.recordVisit(id);

        PlayerWarp after = repo.findById(id).orElseThrow();
        assertThat(after.visits().count()).isEqualTo(seed.visits().count() + 1);
        assertThat(after.visits().uniqueVisitors()).isEqualTo(seed.visits().uniqueVisitors());
        assertThat(after.updatedAt()).isEqualTo(seed.updatedAt());
        assertThat(after.ratings()).isEqualTo(seed.ratings());
    }

    @Test
    void updateRatingOverwritesTheDenormalisedRollup() {
        PlayerWarpId id = repo.save(fullWarp()); // fullWarp seeds RatingSummary.of(90, 20, 4.5, 4.2)
        RatingSummary rollup = RatingSummary.of(37L, 10, 3.7, 3.55);

        repo.updateRating(id, rollup);

        PlayerWarp reloaded = repo.findById(id).orElseThrow();
        assertThat(reloaded.ratings().sum()).isEqualTo(37L);
        assertThat(reloaded.ratings().count()).isEqualTo(10);
        assertThat(reloaded.ratings().average()).isCloseTo(3.7, within(1e-9));
        assertThat(reloaded.ratings().score()).isCloseTo(3.55, within(1e-9));
    }

    @Test
    void refreshFavouriteCountRecomputesFromTheFavouriteRows() {
        PlayerWarpId id = repo.save(fullWarp()); // fullWarp seeds favouriteCount 9
        UUID first = UUID.randomUUID();
        seedFavourite(id.value(), first);
        seedFavourite(id.value(), UUID.randomUUID());
        seedFavourite(id.value(), UUID.randomUUID());

        repo.refreshFavouriteCount(id);
        assertThat(repo.findById(id).orElseThrow().favouriteCount()).isEqualTo(3);

        // Remove one row and refresh: the count converges on the true row count, it is not a running +/-1 bump.
        persistence
                .dsl()
                .transaction(configuration -> DSL.using(configuration)
                        .deleteFrom(PLAYER_WARP_FAVOURITES)
                        .where(PLAYER_WARP_FAVOURITES.WARP_ID.eq(id.value()))
                        .and(PLAYER_WARP_FAVOURITES.PLAYER_UUID.eq(first.toString()))
                        .execute());
        repo.refreshFavouriteCount(id);
        assertThat(repo.findById(id).orElseThrow().favouriteCount()).isEqualTo(2);
    }

    @Test
    void ownerScopedQueriesReadOnlyTheOwnersWarps() {
        repo.save(newWarp(OWNER, "alpha", 1_000L).withAccess(WarpAccess.PUBLIC, Instant.ofEpochMilli(1_000L)));
        repo.save(newWarp(OWNER, "beta", 2_000L)); // stays PRIVATE
        repo.save(newWarp(OTHER, "gamma", 1_500L));

        assertThat(repo.count(OWNER)).isEqualTo(2);
        assertThat(repo.ownedBy(OWNER)).extracting(w -> w.name().value()).containsExactly("alpha", "beta");
        assertThat(repo.publicOwnedBy(OWNER)).extracting(w -> w.name().value()).containsExactly("alpha");
        assertThat(repo.existsByName(PlayerWarpName.of("gamma"))).isTrue();
        assertThat(repo.existsByName(PlayerWarpName.of("delta"))).isFalse();
        assertThat(repo.all()).extracting(w -> w.name().value()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    @Test
    void dueForRentReturnsOnlyLapsedActiveWarpsOldestDueFirst() {
        Instant now = Instant.ofEpochMilli(1_000_000L);
        seedRent("older", WarpStatus.ACTIVE, rent(now.minusSeconds(7200)));
        seedRent("newer", WarpStatus.ACTIVE, rent(now.minusSeconds(3600)));
        seedRent("paid", WarpStatus.ACTIVE, rent(now.plusSeconds(3600))); // still paid through
        seedRent("down", WarpStatus.SUSPENDED, rent(now.minusSeconds(9000))); // not ACTIVE
        repo.save(newWarp(OWNER, "solo", 500L)); // no rent term (NULL), so never enrolled

        assertThat(repo.dueForRent(now, 10)).extracting(w -> w.name().value()).containsExactly("older", "newer");
    }

    @Test
    void dueForRentRespectsTheBatchLimit() {
        Instant now = Instant.ofEpochMilli(1_000_000L);
        for (int i = 0; i < 5; i++) {
            seedRent("due" + i, WarpStatus.ACTIVE, rent(now.minusSeconds(3600L + i)));
        }
        assertThat(repo.dueForRent(now, 2)).hasSize(2);
    }

    @Test
    void suspendedForRentReturnsSuspendedWarpsClosestToArchiveFirst() {
        Instant now = Instant.ofEpochMilli(1_000_000L);
        seedRent("far", WarpStatus.SUSPENDED, suspendedRent(now, now.plusSeconds(86_400)));
        seedRent("soon", WarpStatus.SUSPENDED, suspendedRent(now, now.plusSeconds(3600)));
        seedRent("active", WarpStatus.ACTIVE, rent(now.minusSeconds(60)));

        assertThat(repo.suspendedForRent(10)).extracting(w -> w.name().value()).containsExactly("soon", "far");
    }

    @Test
    void remindableForRentFiltersByHorizonAndStageAndProjectsTheRow() {
        Instant now = Instant.ofEpochMilli(1_000_000L);
        Instant horizon = now.plusSeconds(24 * 3600);
        PlayerWarpId within = seedRent("within", WarpStatus.ACTIVE, rent(now.plusSeconds(20 * 3600)));
        seedRent("beyond", WarpStatus.ACTIVE, rent(now.plusSeconds(40 * 3600))); // past the horizon
        seedRent("lapsed", WarpStatus.ACTIVE, rent(now.minusSeconds(3600))); // already due, not a reminder
        PlayerWarpId maxed = seedRent("maxed", WarpStatus.ACTIVE, rent(now.plusSeconds(10 * 3600)));
        repo.markRentReminded(maxed, 4); // already reminded for every window
        seedRent("suspended", WarpStatus.SUSPENDED, suspendedRent(now, now.plusSeconds(3600)));

        var candidates = repo.remindableForRent(now, horizon, 4, 10);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo(within);
        assertThat(candidates.get(0).warp().value()).isEqualTo("within");
        assertThat(candidates.get(0).owner().uuid()).isEqualTo(OWNER.uuid());
        assertThat(candidates.get(0).remindedStage()).isZero();
        assertThat(candidates.get(0).paidUntil()).isEqualTo(now.plusSeconds(20 * 3600));
    }

    @Test
    void markRentRemindedSetsAndResetsTheStageCounter() {
        Instant now = Instant.ofEpochMilli(1_000_000L);
        PlayerWarpId id = seedRent("staged", WarpStatus.ACTIVE, rent(now.plusSeconds(20 * 3600)));

        repo.markRentReminded(id, 3);
        assertThat(remindedStage(id)).isEqualTo(3);

        repo.markRentReminded(id, 0);
        assertThat(remindedStage(id)).isZero();
    }

    @Test
    void activeSponsorCountAndSlotsReadOnlyLiveSponsors() {
        Instant now = Instant.ofEpochMilli(1_000_000L);
        seedSponsor("live-a", OWNER, now.plusSeconds(3600), 0);
        seedSponsor("live-b", OTHER, now.plusSeconds(7200), 2);
        seedSponsor("expired", OWNER, now.minusSeconds(60), 1); // lapsed, not live
        repo.save(newWarp(OWNER, "plain", 1L)); // never sponsored

        assertThat(repo.activeSponsorSlots(now)).containsExactlyInAnyOrder(0, 2);
        assertThat(repo.activeSponsorCount(OWNER, now)).isEqualTo(1);
        assertThat(repo.activeSponsorCount(OTHER, now)).isEqualTo(1);
    }

    @Test
    void expiredSponsorshipsReturnsLapsedSlottedWarpsOldestExpiryFirst() {
        Instant now = Instant.ofEpochMilli(1_000_000L);
        seedSponsor("older", OWNER, now.minusSeconds(7200), 0);
        seedSponsor("newer", OWNER, now.minusSeconds(3600), 1);
        seedSponsor("still-live", OWNER, now.plusSeconds(3600), 2); // not yet lapsed

        assertThat(repo.expiredSponsorships(now, 10))
                .extracting(w -> w.name().value())
                .containsExactly("older", "newer");
    }

    @Test
    void expireSponsorshipFreesTheSlotAndStampsTheCooldown() {
        Instant now = Instant.ofEpochMilli(1_000_000L);
        PlayerWarpId id = seedSponsor("lapsed", OWNER, now.minusSeconds(60), 3);
        Instant cooldownUntil = now.plusSeconds(259_200);

        repo.expireSponsorship(id, cooldownUntil);

        assertThat(repo.sponsorCooldownUntil(id)).contains(cooldownUntil);
        assertThat(sponsorSlotOf(id)).isNull(); // the slot is freed
        // A freed slot no longer counts as active nor as a re-sweep candidate.
        assertThat(repo.activeSponsorSlots(now)).doesNotContain(3);
        assertThat(repo.expiredSponsorships(now, 10)).isEmpty();
    }

    private PlayerWarpId seedSponsor(String name, PlayerRef owner, Instant until, int slot) {
        Instant stamp = Instant.ofEpochMilli(1L);
        return repo.save(newWarp(owner, name, 1L).withSponsorship(Optional.of(new Sponsorship(until, slot)), stamp));
    }

    private @org.jspecify.annotations.Nullable Integer sponsorSlotOf(PlayerWarpId id) {
        return persistence
                .dsl()
                .select(PLAYER_WARPS.SPONSOR_SLOT)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .fetchOne(PLAYER_WARPS.SPONSOR_SLOT);
    }

    private PlayerWarpId seedRent(String name, WarpStatus status, RentState rent) {
        Instant stamp = Instant.ofEpochMilli(1L);
        return repo.save(newWarp(OWNER, name, 1L).withStatus(status, stamp).withRent(rent, stamp));
    }

    private static RentState rent(Instant paidUntil) {
        return new RentState(paidUntil, Optional.empty(), Optional.empty());
    }

    private static RentState suspendedRent(Instant suspendedAt, Instant archiveAfter) {
        return new RentState(suspendedAt.minusSeconds(1), Optional.of(suspendedAt), Optional.of(archiveAfter));
    }

    private int remindedStage(PlayerWarpId id) {
        return persistence
                .dsl()
                .select(PLAYER_WARPS.RENT_REMINDED_STAGE)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .fetchOne(PLAYER_WARPS.RENT_REMINDED_STAGE);
    }

    /** Seed a committed favourite row for {@code warpId} (the pool runs autoCommit off), for the count recompute. */
    private void seedFavourite(long warpId, UUID player) {
        persistence
                .dsl()
                .transaction(configuration -> DSL.using(configuration)
                        .insertInto(PLAYER_WARP_FAVOURITES)
                        .set(PLAYER_WARP_FAVOURITES.PLAYER_UUID, player.toString())
                        .set(PLAYER_WARP_FAVOURITES.WARP_ID, warpId)
                        .set(PLAYER_WARP_FAVOURITES.ADDED_AT, 1L)
                        .execute());
    }

    private static void assertMatches(PlayerWarp actual, PlayerWarp expected) {
        // Field-for-field equality, with BigDecimal money compared by value so a DECIMAL(20,4) scale never fails it.
        assertThat(actual)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    private static PlayerWarp newWarp(PlayerRef owner, String name, long createdMillis) {
        return PlayerWarp.create(
                owner, owner.name(), PlayerWarpName.of(name), POSITION, Instant.ofEpochMilli(createdMillis));
    }

    private static PlayerWarp fullWarp() {
        return new PlayerWarp(
                Optional.empty(),
                OWNER,
                "Owner",
                PlayerWarpName.of("citadel"),
                Optional.of(DisplayName.of("The Citadel")),
                POSITION,
                Optional.of("survival-1"),
                Optional.of("pvp"),
                Optional.of(WarpDescription.of("A grand fortress above the clouds.")),
                Optional.of(IconSpec.of("DIAMOND_BLOCK")),
                WarpAccess.WHITELIST,
                false, // passwordSet is not persisted through save(); a set password is a separate P4 write
                WarpStatus.SUSPENDED,
                WarpCost.of(new BigDecimal("250.0000"), "coins"),
                WarpEarnings.of(new BigDecimal("40.5000"), "coins"),
                RatingSummary.of(90L, 20, 4.5, 4.2),
                new VisitSummary(1_234L, 78),
                9,
                Optional.of(new Sponsorship(Instant.ofEpochMilli(5_000_000L), 2)),
                Optional.of(new RentState(
                        Instant.ofEpochMilli(6_000_000L),
                        Optional.of(Instant.ofEpochMilli(7_000_000L)),
                        Optional.of(Instant.ofEpochMilli(8_000_000L)))),
                new WarpEffects(
                        Optional.of("BLOCK_PORTAL_TRAVEL"),
                        Optional.of("ENTITY_ENDERMAN_TELEPORT"),
                        Optional.of("PORTAL"),
                        Optional.of("REVERSE_PORTAL")),
                new WarpTimingOverrides(Optional.of(2.5), Optional.of(10.0)),
                Instant.ofEpochMilli(1_000L),
                Instant.ofEpochMilli(2_000L));
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

    private static final class NoopLogger implements Logger {
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
