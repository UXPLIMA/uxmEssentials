package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
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
 * End-to-end coverage of {@link JooqWarpRatingRewardStore} against the default embedded SQLite backend with the
 * Flyway V1-V70 migrations applied. It proves the round-trip (record → hasAwarded), that the dedup is scoped to the
 * exact {@code (subject, warp, rewardId)} key, and that a repeated record of the same grant is idempotent rather
 * than a second row or a thrown conflict.
 */
class JooqWarpRatingRewardStoreTest {

    private static final PlayerWarpId WARP = PlayerWarpId.of(1L);
    private static final PlayerWarpId OTHER_WARP = PlayerWarpId.of(2L);
    private static final Instant AT = Instant.ofEpochMilli(1_000L);

    private Persistence persistence;
    private JooqWarpRatingRewardStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqWarpRatingRewardStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void recordingAGrantMakesHasAwardedTrue() {
        UUID subject = UUID.randomUUID();

        store.record(subject, WARP, "rate", "RATER", AT);

        assertThat(store.hasAwarded(subject, WARP, "rate")).isTrue();
    }

    @Test
    void dedupIsScopedToTheExactSubjectWarpAndRewardId() {
        UUID subject = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        store.record(subject, WARP, "rate", "RATER", AT);

        assertThat(store.hasAwarded(subject, WARP, "rater:" + other)).isFalse();
        assertThat(store.hasAwarded(subject, OTHER_WARP, "rate")).isFalse();
        assertThat(store.hasAwarded(other, WARP, "rate")).isFalse();
    }

    @Test
    void recordingTheSameGrantTwiceIsIdempotent() {
        UUID subject = UUID.randomUUID();

        store.record(subject, WARP, "rate", "RATER", AT);
        store.record(subject, WARP, "rate", "RATER", AT.plusMillis(5_000L));

        assertThat(store.hasAwarded(subject, WARP, "rate")).isTrue();
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
