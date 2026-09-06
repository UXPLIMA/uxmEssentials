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
 * End-to-end coverage of {@link JooqWarpWhitelistStore} against the default embedded SQLite backend with the
 * Flyway V1-V70 migrations applied. It proves the round-trip (add → contains → list), that a repeated add is
 * idempotent rather than a second row, that a remove clears the entry, and that the whitelist is scoped to its
 * own warp.
 */
class JooqWarpWhitelistStoreTest {

    private static final PlayerWarpId WARP = PlayerWarpId.of(1L);
    private static final PlayerWarpId OTHER_WARP = PlayerWarpId.of(2L);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);

    private Persistence persistence;
    private JooqWarpWhitelistStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqWarpWhitelistStore(persistence.dsl(), CLOCK);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void addingAPlayerMakesContainsTrueAndListsThem() {
        UUID player = UUID.randomUUID();

        store.add(WARP, player);

        assertThat(store.contains(WARP, player)).isTrue();
        assertThat(store.list(WARP)).containsExactly(player);
    }

    @Test
    void addingTheSamePlayerTwiceIsIdempotent() {
        UUID player = UUID.randomUUID();

        store.add(WARP, player);
        store.add(WARP, player);

        assertThat(store.list(WARP)).containsExactly(player);
    }

    @Test
    void removingAPlayerClearsTheEntry() {
        UUID player = UUID.randomUUID();
        store.add(WARP, player);

        store.remove(WARP, player);

        assertThat(store.contains(WARP, player)).isFalse();
        assertThat(store.list(WARP)).isEmpty();
    }

    @Test
    void listReturnsEveryWhitelistedPlayerForThatWarpOnly() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.add(WARP, first);
        store.add(WARP, second);
        store.add(OTHER_WARP, UUID.randomUUID());

        assertThat(store.list(WARP)).containsExactlyInAnyOrder(first, second);
        assertThat(store.contains(OTHER_WARP, first)).isFalse();
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
