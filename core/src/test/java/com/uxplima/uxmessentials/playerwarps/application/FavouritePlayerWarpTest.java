package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The favourite use case over the in-memory fakes: a star adds the row and recomputes the count, an un-star removes
 * it and recomputes, the already-in-state paths are pure no-ops, and the persisted count is always the true row
 * count rather than a running bump: the property that makes it drift-proof under a double-click race.
 */
class FavouritePlayerWarpTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Favourites favourites;
    private PlayerWarpTestSupport.Sink sink;
    private FavouritePlayerWarp favourite;
    private PlayerRef actor;
    private PlayerWarpId warpId;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        favourites = new PlayerWarpTestSupport.Favourites();
        repository.countFavouritesFrom(favourites);
        sink = new PlayerWarpTestSupport.Sink();
        favourite = new FavouritePlayerWarp(repository, favourites, PlayerWarpTestSupport.notifier(sink));
        PlayerRef owner = PlayerWarpTestSupport.ref("Owner");
        actor = PlayerWarpTestSupport.ref("Viewer");
        warpId = repository.put(PlayerWarpTestSupport.warp(owner, "hub")).id().orElseThrow();
    }

    @Test
    void starringAddsTheFavouriteAndRefreshesTheCount() {
        Result<Unit, PlayerWarpError> result = favourite.favourite(actor, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(favourites.contains(actor.uuid(), warpId)).isTrue();
        assertThat(repository.favouriteCounts.get(warpId)).isEqualTo(1);
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.favourited"));
    }

    @Test
    void starringAnAlreadyFavouritedWarpIsANoOp() {
        favourite.favourite(actor, HUB);
        repository.favouriteCounts.clear();
        sink.delivered.clear();

        Result<Unit, PlayerWarpError> result = favourite.favourite(actor, HUB);

        assertThat(result.isOk()).isTrue();
        // The no-op path neither re-adds a row nor recomputes the count.
        assertThat(repository.favouriteCounts).isEmpty();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.already-favourited"));
    }

    @Test
    void theCountRecomputesFromTheRowsNotARunningBump() {
        PlayerRef second = PlayerWarpTestSupport.ref("Second");
        favourite.favourite(actor, HUB);
        favourite.favourite(second, HUB);

        assertThat(repository.favouriteCounts.get(warpId)).isEqualTo(2);
    }

    @Test
    void unstarringRemovesTheFavouriteAndRefreshesTheCount() {
        favourite.favourite(actor, HUB);

        Result<Unit, PlayerWarpError> result = favourite.unfavourite(actor, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(favourites.contains(actor.uuid(), warpId)).isFalse();
        assertThat(repository.favouriteCounts.get(warpId)).isEqualTo(0);
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.unfavourited"));
    }

    @Test
    void unstarringAWarpThatWasNotAFavouriteIsANoOp() {
        Result<Unit, PlayerWarpError> result = favourite.unfavourite(actor, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.favouriteCounts).isEmpty();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.not-favourited"));
    }

    @Test
    void favouritingOrUnfavouritingAMissingWarpIsNotFound() {
        assertThat(favourite.favourite(actor, PlayerWarpName.of("ghost")).errorOrThrow())
                .isEqualTo(PlayerWarpError.NOT_FOUND);
        assertThat(favourite.unfavourite(actor, PlayerWarpName.of("ghost")).errorOrThrow())
                .isEqualTo(PlayerWarpError.NOT_FOUND);
    }
}
