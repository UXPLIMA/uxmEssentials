package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.Page;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.VisitSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEffects;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;
import com.uxplima.uxmessentials.playerwarps.domain.WarpSort;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqPlayerWarpBrowse} against the default embedded SQLite backend with the Flyway
 * V1-V71 migrations applied. It proves the read model returns exactly one filtered, sorted page of {@link WarpCard}s
 * with a stable total: every {@link WarpSort} orders correctly (with the {@code id} tiebreaker exercised on a
 * distance tie), the filters ({@code onlyActive}, {@code access}, {@code search}, {@code favouritesOf}) select the
 * right rows, {@code viewerFavourited} is true only for the viewer's own favourites, {@code sponsored} tracks the
 * clock, paging returns disjoint pages, and a 200-row table still hands back a single bounded page. The random
 * ordering and {@link JooqPlayerWarpRepository#reshuffle()} are driven through an injected deterministic key source.
 */
class JooqPlayerWarpBrowseTest {

    private static final WorldRef WORLD_A = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef WORLD_B = new WorldRef(UUID.randomUUID(), "nether");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final PlayerRef OTHER_OWNER = new PlayerRef(UUID.randomUUID(), "Other");
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID SOMEONE_ELSE = UUID.randomUUID();
    // now = 10_000ms, so sponsored_until=20_000 is live and 5_000 has lapsed.
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(10_000L), ZoneOffset.UTC);

    private Persistence persistence;
    private JooqPlayerWarpRepository repo;
    private JooqPlayerWarpBrowse browse;
    private JooqWarpFavouriteStore favourites;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repo = new JooqPlayerWarpRepository(persistence.dsl());
        browse = new JooqPlayerWarpBrowse(persistence.dsl(), CLOCK);
        favourites = new JooqWarpFavouriteStore(persistence.dsl(), CLOCK);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void everySortOrdersTheSameFixtureCorrectly() {
        seedFixture();

        assertThat(names(browsePublic(WarpSort.ALPHABETICAL))).containsExactly("alpha", "bravo", "charlie");
        assertThat(names(browsePublic(WarpSort.NEWEST))).containsExactly("bravo", "charlie", "alpha");
        assertThat(names(browsePublic(WarpSort.OLDEST))).containsExactly("alpha", "charlie", "bravo");
        assertThat(names(browsePublic(WarpSort.VISITS))).containsExactly("bravo", "charlie", "alpha");
        assertThat(names(browsePublic(WarpSort.UNIQUE_VISITORS))).containsExactly("bravo", "charlie", "alpha");
        assertThat(names(browsePublic(WarpSort.RATING))).containsExactly("bravo", "charlie", "alpha");
        assertThat(names(browsePublic(WarpSort.RATING_COUNT))).containsExactly("bravo", "charlie", "alpha");
        assertThat(names(browsePublic(WarpSort.FAVOURITES))).containsExactly("bravo", "charlie", "alpha");
    }

    @Test
    void distanceOrdersByNearestWithinTheViewersWorldAndBreaksTiesById() {
        seedFixture(); // alpha x=0, charlie x=10 (tie at distance 25 from x=5), bravo x=100
        save(
                OWNER,
                "far-nether",
                WORLD_B,
                5,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1L,
                0,
                0,
                0,
                0,
                0,
                null);

        Page<WarpCard> page = browse.page(distanceQuery(Position.of(WORLD_A, 5, 64, 0)));

        // alpha and charlie tie on squared distance (25); the id tiebreaker keeps the earlier-inserted alpha first.
        assertThat(names(page)).containsExactly("alpha", "charlie", "bravo");
        assertThat(page.totalCount())
                .as("the other-world warp is filtered out of a distance browse")
                .isEqualTo(3L);
    }

    @Test
    void distanceFallsBackToAlphabeticalWithoutAViewerPosition() {
        seedFixture();

        assertThat(names(browsePublic(WarpSort.DISTANCE))).containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void onlyActiveHidesSuspendedAndArchivedWarps() {
        save(
                OWNER,
                "live",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1L,
                0,
                0,
                0,
                0,
                0,
                null);
        save(
                OWNER,
                "suspended",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.SUSPENDED,
                null,
                null,
                null,
                2L,
                0,
                0,
                0,
                0,
                0,
                null);
        save(
                OWNER,
                "archived",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ARCHIVED,
                null,
                null,
                null,
                3L,
                0,
                0,
                0,
                0,
                0,
                null);

        Page<WarpCard> page = browsePublic(WarpSort.ALPHABETICAL);

        assertThat(names(page)).containsExactly("live");
        assertThat(page.totalCount()).isEqualTo(1L);
    }

    @Test
    void accessFilterKeepsAPrivateWarpOutOfAPublicBrowse() {
        save(
                OWNER,
                "open",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1L,
                0,
                0,
                0,
                0,
                0,
                null);
        save(
                OWNER,
                "hidden",
                WORLD_A,
                0,
                0,
                WarpAccess.PRIVATE,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                2L,
                0,
                0,
                0,
                0,
                0,
                null);

        assertThat(names(browsePublic(WarpSort.ALPHABETICAL))).containsExactly("open");
    }

    @Test
    void searchMatchesNameAndDisplayNameCaseInsensitively() {
        save(
                OWNER,
                "spawn",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                "Central Hub",
                1L,
                0,
                0,
                0,
                0,
                0,
                null);
        save(
                OWNER,
                "market",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                "The Bazaar",
                2L,
                0,
                0,
                0,
                0,
                0,
                null);

        assertThat(names(searchBrowse("SPAWN"))).containsExactly("spawn"); // matched on the name
        assertThat(names(searchBrowse("bazaar"))).containsExactly("market"); // matched on the display name
        assertThat(names(searchBrowse("zzz"))).isEmpty();
    }

    @Test
    void favouritesFilterAndViewerFlagTrackThePlayer() {
        PlayerWarpId starred = save(
                OWNER,
                "starred",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1L,
                0,
                0,
                0,
                0,
                0,
                null);
        PlayerWarpId plain = save(
                OWNER,
                "plain",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                2L,
                0,
                0,
                0,
                0,
                0,
                null);
        favourites.add(VIEWER, starred);
        favourites.add(SOMEONE_ELSE, plain); // someone else's favourite must not flip the viewer's flag

        Page<WarpCard> all = browsePublic(WarpSort.ALPHABETICAL);
        assertThat(card(all, "starred").viewerFavourited()).isTrue();
        assertThat(card(all, "plain").viewerFavourited()).isFalse();

        Page<WarpCard> onlyStarred = browse.page(favouritesQuery(VIEWER));
        assertThat(names(onlyStarred)).containsExactly("starred");
        assertThat(plain).isNotEqualTo(starred); // both warps exist; the filter, not existence, narrows the page
    }

    @Test
    void pagingReturnsDisjointPagesWithAStableTotal() {
        for (String name : List.of("aaa", "bbb", "ccc", "ddd", "eee")) {
            save(
                    OWNER,
                    name,
                    WORLD_A,
                    0,
                    0,
                    WarpAccess.PUBLIC,
                    WarpStatus.ACTIVE,
                    null,
                    null,
                    null,
                    1L,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null);
        }

        Page<WarpCard> first = browse.page(WarpQuery.publicBrowse(VIEWER, WarpSort.ALPHABETICAL, 0, 2));
        Page<WarpCard> second = browse.page(WarpQuery.publicBrowse(VIEWER, WarpSort.ALPHABETICAL, 1, 2));
        Page<WarpCard> third = browse.page(WarpQuery.publicBrowse(VIEWER, WarpSort.ALPHABETICAL, 2, 2));

        assertThat(names(first)).containsExactly("aaa", "bbb");
        assertThat(names(second)).containsExactly("ccc", "ddd");
        assertThat(names(third)).containsExactly("eee");
        assertThat(first.totalCount()).isEqualTo(5L);
        assertThat(second.totalCount()).isEqualTo(5L);
        assertThat(third.totalCount()).isEqualTo(5L);
        assertThat(third.hasNext()).isFalse();
    }

    @Test
    void aPageStaysBoundedRegardlessOfTableSize() {
        for (int n = 0; n < 200; n++) {
            save(
                    OWNER,
                    "warp-" + String.format("%03d", n),
                    WORLD_A,
                    0,
                    0,
                    WarpAccess.PUBLIC,
                    WarpStatus.ACTIVE,
                    null,
                    null,
                    null,
                    1000L + n,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null);
        }

        Page<WarpCard> first = browse.page(WarpQuery.publicBrowse(VIEWER, WarpSort.ALPHABETICAL, 0, 10));
        Page<WarpCard> last = browse.page(WarpQuery.publicBrowse(VIEWER, WarpSort.ALPHABETICAL, 19, 10));

        assertThat(first.items())
                .as("a page is capped at pageSize even on a 200-row table")
                .hasSize(10);
        assertThat(first.totalCount()).isEqualTo(200L);
        assertThat(first.hasNext()).isTrue();
        assertThat(last.items()).hasSize(10);
        assertThat(last.hasNext()).isFalse();
        assertThat(names(first)).doesNotContainAnyElementsOf(names(last));
    }

    @Test
    void randomSortOrdersByThePersistedRandomKey() {
        JooqPlayerWarpRepository seeded = repoWithRandomKeys(30L, 10L, 20L);
        seeded.save(build(
                OWNER,
                "alpha",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1L,
                0,
                0,
                0,
                0,
                0,
                null));
        seeded.save(build(
                OWNER,
                "bravo",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                2L,
                0,
                0,
                0,
                0,
                0,
                null));
        seeded.save(build(
                OWNER,
                "charlie",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                3L,
                0,
                0,
                0,
                0,
                0,
                null));

        // random_sort asc: bravo(10) < charlie(20) < alpha(30).
        assertThat(names(browse.page(WarpQuery.publicBrowse(VIEWER, WarpSort.RANDOM, 0, 50))))
                .containsExactly("bravo", "charlie", "alpha");
    }

    @Test
    void reshuffleRewritesTheRandomOrder() {
        // First three keys stamp the inserts; the next three are what reshuffle assigns to ids 1, 2, 3.
        JooqPlayerWarpRepository seeded = repoWithRandomKeys(30L, 10L, 20L, 5L, 15L, 25L);
        seeded.save(build(
                OWNER,
                "alpha",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1L,
                0,
                0,
                0,
                0,
                0,
                null));
        seeded.save(build(
                OWNER,
                "bravo",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                2L,
                0,
                0,
                0,
                0,
                0,
                null));
        seeded.save(build(
                OWNER,
                "charlie",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                3L,
                0,
                0,
                0,
                0,
                0,
                null));

        assertThat(names(browse.page(WarpQuery.publicBrowse(VIEWER, WarpSort.RANDOM, 0, 50))))
                .containsExactly("bravo", "charlie", "alpha");

        assertThat(seeded.reshuffle()).isEqualTo(3);

        // alpha=5, bravo=15, charlie=25 now.
        assertThat(names(browse.page(WarpQuery.publicBrowse(VIEWER, WarpSort.RANDOM, 0, 50))))
                .containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void aCardCarriesTheFlattenedDisplayColumnsAndTracksSponsorship() {
        PlayerWarp warp = new PlayerWarp(
                Optional.empty(),
                OWNER,
                "Owner",
                PlayerWarpName.of("citadel"),
                Optional.of(DisplayName.of("The Citadel")),
                new Position(WORLD_A, 12.5, 64.0, -30.5, 0f, 0f),
                Optional.of("survival-1"),
                Optional.of("pvp"),
                Optional.of(WarpDescription.of("A grand fortress.")),
                Optional.of(IconSpec.of("DIAMOND_BLOCK")),
                WarpAccess.PUBLIC,
                false,
                WarpStatus.ACTIVE,
                WarpCost.of(new BigDecimal("250.0000"), "coins"),
                WarpEarnings.of(new BigDecimal("40.5000"), "coins"),
                RatingSummary.of(90L, 20, 4.5, 4.2),
                new VisitSummary(1_234L, 78),
                9,
                Optional.of(
                        new com.uxplima.uxmessentials.playerwarps.domain.Sponsorship(Instant.ofEpochMilli(20_000L), 1)),
                Optional.empty(),
                new WarpEffects(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                new WarpTimingOverrides(Optional.empty(), Optional.empty()),
                Instant.ofEpochMilli(1_000L),
                Instant.ofEpochMilli(2_000L));
        repo.save(warp);

        WarpCard card = card(browsePublic(WarpSort.ALPHABETICAL), "citadel");

        assertThat(card.displayName()).isEqualTo("The Citadel");
        assertThat(card.ownerName()).isEqualTo("Owner");
        assertThat(card.world()).isEqualTo("world");
        assertThat(card.server()).isEqualTo("survival-1");
        assertThat(card.category()).isEqualTo("pvp");
        assertThat(card.icon()).isEqualTo("DIAMOND_BLOCK");
        assertThat(card.visits()).isEqualTo(1_234L);
        assertThat(card.uniqueVisitors()).isEqualTo(78);
        assertThat(card.ratingAvg()).isEqualTo(4.5);
        assertThat(card.ratingCount()).isEqualTo(20);
        assertThat(card.favourites()).isEqualTo(9);
        assertThat(card.price()).isEqualByComparingTo("250.0000");
        assertThat(card.currency()).isEqualTo("coins");
        assertThat(card.access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(card.sponsored())
                .as("sponsored_until 20_000 is live at now=10_000")
                .isTrue();
    }

    @Test
    void aLapsedSponsorshipDoesNotFlagTheCard() {
        save(
                OWNER,
                "expired",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1L,
                0,
                0,
                0,
                0,
                0,
                5_000L);
        save(
                OWNER,
                "never",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                2L,
                0,
                0,
                0,
                0,
                0,
                null);

        Page<WarpCard> page = browsePublic(WarpSort.ALPHABETICAL);

        assertThat(card(page, "expired").sponsored())
                .as("sponsored_until 5_000 has lapsed at now=10_000")
                .isFalse();
        assertThat(card(page, "never").sponsored()).isFalse();
    }

    @Test
    void activeSponsorsReturnsLivePublicSponsorsOrderedBySlot() {
        seedSponsor("second", 1, 20_000L, WarpAccess.PUBLIC, WarpStatus.ACTIVE);
        seedSponsor("first", 0, 20_000L, WarpAccess.PUBLIC, WarpStatus.ACTIVE);
        seedSponsor("lapsed", 2, 5_000L, WarpAccess.PUBLIC, WarpStatus.ACTIVE); // sponsored_until 5_000 < now
        seedSponsor("hidden", 3, 20_000L, WarpAccess.PRIVATE, WarpStatus.ACTIVE); // never public-browsed
        seedSponsor("down", 4, 20_000L, WarpAccess.PUBLIC, WarpStatus.SUSPENDED); // not active

        List<WarpCard> sponsors = browse.activeSponsors(10);

        assertThat(sponsors).extracting(WarpCard::name).containsExactly("first", "second");
        assertThat(sponsors).allMatch(WarpCard::sponsored);
    }

    @Test
    void activeSponsorsRespectsTheLimit() {
        seedSponsor("aaa", 0, 20_000L, WarpAccess.PUBLIC, WarpStatus.ACTIVE);
        seedSponsor("bbb", 1, 20_000L, WarpAccess.PUBLIC, WarpStatus.ACTIVE);

        assertThat(browse.activeSponsors(1)).extracting(WarpCard::name).containsExactly("aaa");
    }

    @Test
    void ownerFilterNarrowsToOneOwner() {
        save(
                OWNER,
                "mine",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1L,
                0,
                0,
                0,
                0,
                0,
                null);
        save(
                OTHER_OWNER,
                "theirs",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                2L,
                0,
                0,
                0,
                0,
                0,
                null);

        WarpQuery query = new WarpQuery(
                Optional.empty(),
                Optional.of(WarpAccess.PUBLIC),
                Optional.empty(),
                Optional.of(OWNER.uuid()),
                Optional.empty(),
                Optional.empty(),
                true,
                WarpSort.ALPHABETICAL,
                0,
                50,
                VIEWER,
                Optional.empty());

        assertThat(names(browse.page(query))).containsExactly("mine");
    }

    // --- fixtures & helpers -------------------------------------------------------------------------------------

    /** alpha, bravo, charlie in WORLD_A with pairwise-distinct visits / uniques / ratings / favourites / created. */
    private void seedFixture() {
        save(
                OWNER,
                "alpha",
                WORLD_A,
                0,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                1_000L,
                5,
                2,
                2.0,
                1,
                0,
                null);
        save(
                OWNER,
                "bravo",
                WORLD_A,
                100,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                3_000L,
                50,
                30,
                4.5,
                100,
                9,
                null);
        save(
                OWNER,
                "charlie",
                WORLD_A,
                10,
                0,
                WarpAccess.PUBLIC,
                WarpStatus.ACTIVE,
                null,
                null,
                null,
                2_000L,
                20,
                10,
                3.0,
                10,
                3,
                null);
    }

    /** Seed a warp holding a sponsorship in {@code slot} until {@code until}, at the given access and status. */
    private void seedSponsor(String name, int slot, long until, WarpAccess access, WarpStatus status) {
        PlayerWarp warp = build(OWNER, name, WORLD_A, 0, 0, access, status, null, null, null, 1L, 0, 0, 0, 0, 0, null);
        repo.save(warp.withSponsorship(
                Optional.of(new com.uxplima.uxmessentials.playerwarps.domain.Sponsorship(
                        Instant.ofEpochMilli(until), slot)),
                Instant.ofEpochMilli(1L)));
    }

    private Page<WarpCard> browsePublic(WarpSort sort) {
        return browse.page(WarpQuery.publicBrowse(VIEWER, sort, 0, 50));
    }

    private WarpQuery distanceQuery(Position viewerPosition) {
        return new WarpQuery(
                Optional.empty(),
                Optional.of(WarpAccess.PUBLIC),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                WarpSort.DISTANCE,
                0,
                50,
                VIEWER,
                Optional.of(viewerPosition));
    }

    private Page<WarpCard> searchBrowse(String text) {
        return browse.page(new WarpQuery(
                Optional.empty(),
                Optional.of(WarpAccess.PUBLIC),
                Optional.empty(),
                Optional.empty(),
                Optional.of(text),
                Optional.empty(),
                true,
                WarpSort.ALPHABETICAL,
                0,
                50,
                VIEWER,
                Optional.empty()));
    }

    private WarpQuery favouritesQuery(UUID player) {
        return new WarpQuery(
                Optional.empty(),
                Optional.of(WarpAccess.PUBLIC),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(player),
                true,
                WarpSort.ALPHABETICAL,
                0,
                50,
                VIEWER,
                Optional.empty());
    }

    private JooqPlayerWarpRepository repoWithRandomKeys(long... keys) {
        AtomicInteger cursor = new AtomicInteger();
        Function<UUID, String> names = UUID::toString;
        return new JooqPlayerWarpRepository(persistence.dsl(), names, () -> keys[cursor.getAndIncrement()]);
    }

    private PlayerWarpId save(
            PlayerRef owner,
            String name,
            WorldRef world,
            double x,
            double z,
            WarpAccess access,
            WarpStatus status,
            @Nullable String category,
            @Nullable String server,
            @Nullable String displayName,
            long createdMillis,
            long visits,
            int unique,
            double ratingScore,
            int ratingCount,
            int favourites,
            @Nullable Long sponsoredUntil) {
        return repo.save(build(
                owner,
                name,
                world,
                x,
                z,
                access,
                status,
                category,
                server,
                displayName,
                createdMillis,
                visits,
                unique,
                ratingScore,
                ratingCount,
                favourites,
                sponsoredUntil));
    }

    private static PlayerWarp build(
            PlayerRef owner,
            String name,
            WorldRef world,
            double x,
            double z,
            WarpAccess access,
            WarpStatus status,
            @Nullable String category,
            @Nullable String server,
            @Nullable String displayName,
            long createdMillis,
            long visits,
            int unique,
            double ratingScore,
            int ratingCount,
            int favourites,
            @Nullable Long sponsoredUntil) {
        return new PlayerWarp(
                Optional.empty(),
                owner,
                owner.name(),
                PlayerWarpName.of(name),
                Optional.ofNullable(displayName).map(DisplayName::of),
                new Position(world, x, 64.0, z, 0f, 0f),
                Optional.ofNullable(server),
                Optional.ofNullable(category),
                Optional.empty(),
                Optional.empty(),
                access,
                false,
                status,
                WarpCost.of(BigDecimal.ZERO, "default"),
                WarpEarnings.of(BigDecimal.ZERO, "default"),
                RatingSummary.of(Math.round(ratingScore * ratingCount), ratingCount, ratingScore, ratingScore),
                new VisitSummary(visits, unique),
                favourites,
                Optional.ofNullable(sponsoredUntil)
                        .map(until -> new com.uxplima.uxmessentials.playerwarps.domain.Sponsorship(
                                Instant.ofEpochMilli(until), 0)),
                Optional.empty(),
                new WarpEffects(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                new WarpTimingOverrides(Optional.empty(), Optional.empty()),
                Instant.ofEpochMilli(createdMillis),
                Instant.ofEpochMilli(createdMillis));
    }

    private static List<String> names(Page<WarpCard> page) {
        return page.items().stream().map(WarpCard::name).toList();
    }

    private static WarpCard card(Page<WarpCard> page, String name) {
        return page.items().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no card named " + name + " on the page"));
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
