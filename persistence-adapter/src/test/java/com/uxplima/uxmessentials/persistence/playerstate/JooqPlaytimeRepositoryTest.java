package com.uxplima.uxmessentials.persistence.playerstate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqPlaytimeRepository} against the default embedded SQLite backend with the
 * Flyway ladder applied (the tested default of the backend-parity matrix; the network backends run the same DSL
 * behind Testcontainers, which this environment may not have). It proves the upsert accumulates per-day deltas
 * rather than overwriting, that {@link PlaytimeRepository#summaryOf} computes the today / last-7-days /
 * last-30-days / all-time windows as range SUMs over a seeded multi-day set keeping active and AFK apart, and that
 * {@code reset} clears a player's rows without touching another player's.
 */
class JooqPlaytimeRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    private Persistence persistence;
    private JooqPlaytimeRepository repository;
    private UUID player;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqPlaytimeRepository(persistence.dsl());
        player = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void addSecondsInsertsThenAccumulatesOnTheSamePlayerDay() {
        repository.addSeconds(player, TODAY, 60L, 0L);
        repository.addSeconds(player, TODAY, 60L, 30L);

        PlaytimeSummary summary = repository.summaryOf(player, TODAY);

        assertThat(summary.todayActive().toSeconds()).isEqualTo(120L);
        assertThat(summary.todayAfk().toSeconds()).isEqualTo(30L);
    }

    @Test
    void summaryComputesEachWindowOverASeededMultiDaySet() {
        // today: 100 active / 10 afk
        repository.addSeconds(player, TODAY, 100L, 10L);
        // 3 days ago (inside the 7- and 30-day windows, outside today): 200 active / 20 afk
        repository.addSeconds(player, TODAY.minusDays(3), 200L, 20L);
        // 10 days ago (inside the 30-day window, outside the 7-day): 400 active / 40 afk
        repository.addSeconds(player, TODAY.minusDays(10), 400L, 40L);
        // 100 days ago (all-time only): 800 active / 80 afk
        repository.addSeconds(player, TODAY.minusDays(100), 800L, 80L);

        PlaytimeSummary summary = repository.summaryOf(player, TODAY);

        assertThat(summary.todayActive().toSeconds()).isEqualTo(100L);
        assertThat(summary.todayAfk().toSeconds()).isEqualTo(10L);
        assertThat(summary.weekActive().toSeconds()).isEqualTo(300L); // today + 3 days ago
        assertThat(summary.weekAfk().toSeconds()).isEqualTo(30L);
        assertThat(summary.monthActive().toSeconds()).isEqualTo(700L); // + 10 days ago
        assertThat(summary.monthAfk().toSeconds()).isEqualTo(70L);
        assertThat(summary.totalActive().toSeconds()).isEqualTo(1500L); // + 100 days ago
        assertThat(summary.totalAfk().toSeconds()).isEqualTo(150L);
    }

    @Test
    void summaryOfAnUntrackedPlayerIsAllZero() {
        PlaytimeSummary summary = repository.summaryOf(UUID.randomUUID(), TODAY);

        assertThat(summary).isEqualTo(PlaytimeSummary.empty());
    }

    @Test
    void resetClearsOnlyTheGivenPlayersRows() {
        UUID other = UUID.randomUUID();
        repository.addSeconds(player, TODAY, 100L, 10L);
        repository.addSeconds(other, TODAY, 200L, 20L);

        repository.reset(player);

        assertThat(repository.summaryOf(player, TODAY)).isEqualTo(PlaytimeSummary.empty());
        assertThat(repository.summaryOf(other, TODAY).todayActive().toSeconds()).isEqualTo(200L);
    }

    @Test
    void resetAllClearsEveryPlayersRows() {
        UUID other = UUID.randomUUID();
        repository.addSeconds(player, TODAY, 100L, 10L);
        repository.addSeconds(other, TODAY, 200L, 20L);

        repository.resetAll();

        assertThat(repository.summaryOf(player, TODAY)).isEqualTo(PlaytimeSummary.empty());
        assertThat(repository.summaryOf(other, TODAY)).isEqualTo(PlaytimeSummary.empty());
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
