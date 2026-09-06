package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.RatingTally;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWarpRatingStore} against the default embedded SQLite backend with the Flyway
 * V1-V70 migrations applied. It proves the upsert (a re-rate overwrites the one row rather than adding a second), the
 * per-warp tally (summed only over the warp's own raters, {@code (0, 0)} when nobody has rated it), and the
 * cross-warp global mean the Bayesian prior reads (0.0 on an empty table).
 */
class JooqWarpRatingStoreTest {

    private static final PlayerWarpId WARP = PlayerWarpId.of(1L);
    private static final PlayerWarpId OTHER_WARP = PlayerWarpId.of(2L);
    private static final Instant AT = Instant.ofEpochMilli(1_000L);

    private Persistence persistence;
    private JooqWarpRatingStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqWarpRatingStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void reRatingOverwritesTheSingleVoteRatherThanAddingASecond() {
        UUID player = UUID.randomUUID();

        store.put(WARP, player, 3, AT);
        store.put(WARP, player, 5, AT.plusMillis(10));

        RatingTally tally = store.tally(WARP);
        assertThat(tally.count()).isEqualTo(1);
        assertThat(tally.sum()).isEqualTo(5L);
    }

    @Test
    void tallySumsOnlyTheWarpsOwnRaters() {
        store.put(WARP, UUID.randomUUID(), 5, AT);
        store.put(WARP, UUID.randomUUID(), 3, AT);
        store.put(WARP, UUID.randomUUID(), 4, AT);
        store.put(OTHER_WARP, UUID.randomUUID(), 1, AT); // a different warp's vote must not bleed in

        RatingTally tally = store.tally(WARP);
        assertThat(tally.count()).isEqualTo(3);
        assertThat(tally.sum()).isEqualTo(12L);
    }

    @Test
    void anUnratedWarpTalliesToZero() {
        assertThat(store.tally(WARP)).isEqualTo(RatingTally.empty());
    }

    @Test
    void globalMeanAveragesEveryStarAcrossEveryWarp() {
        store.put(WARP, UUID.randomUUID(), 5, AT);
        store.put(WARP, UUID.randomUUID(), 3, AT);
        store.put(OTHER_WARP, UUID.randomUUID(), 4, AT);

        assertThat(store.globalMean()).isCloseTo(4.0, within(1e-9)); // (5 + 3 + 4) / 3
    }

    @Test
    void globalMeanIsZeroWhenThereAreNoRatings() {
        assertThat(store.globalMean()).isZero();
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
