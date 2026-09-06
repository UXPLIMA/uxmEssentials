package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWarpFavouriteStore} against the default embedded SQLite backend with the
 * Flyway V1-V70 migrations applied. It proves the round-trip (add → contains → listFor), that a repeated add is
 * idempotent rather than a second row, that {@code listFor} returns only the queried player's warps, and that a
 * remove clears the star.
 */
class JooqWarpFavouriteStoreTest {

    private static final PlayerWarpId WARP = PlayerWarpId.of(1L);
    private static final PlayerWarpId OTHER_WARP = PlayerWarpId.of(2L);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);

    private Persistence persistence;
    private JooqWarpFavouriteStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqWarpFavouriteStore(persistence.dsl(), CLOCK);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void starringAWarpMakesContainsTrueAndListsIt() {
        UUID player = UUID.randomUUID();

        store.add(player, WARP);

        assertThat(store.contains(player, WARP)).isTrue();
        assertThat(store.listFor(player)).containsExactly(WARP);
    }

    @Test
    void starringTheSameWarpTwiceIsIdempotent() {
        UUID player = UUID.randomUUID();

        store.add(player, WARP);
        store.add(player, WARP);

        assertThat(store.listFor(player)).containsExactly(WARP);
    }

    @Test
    void listForReturnsOnlyTheQueriedPlayersFavourites() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        store.add(player, WARP);
        store.add(player, OTHER_WARP);
        store.add(other, WARP);

        assertThat(store.listFor(player)).containsExactlyInAnyOrder(WARP, OTHER_WARP);
        assertThat(store.listFor(other)).containsExactly(WARP);
    }

    @Test
    void unstarringClearsTheFavourite() {
        UUID player = UUID.randomUUID();
        store.add(player, WARP);

        store.remove(player, WARP);

        assertThat(store.contains(player, WARP)).isFalse();
        assertThat(store.listFor(player)).isEmpty();
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
