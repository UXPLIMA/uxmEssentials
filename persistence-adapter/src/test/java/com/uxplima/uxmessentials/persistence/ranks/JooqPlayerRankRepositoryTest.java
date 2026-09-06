package com.uxplima.uxmessentials.persistence.ranks;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqPlayerRankRepository} against the default embedded SQLite backend with the
 * Flyway V74 {@code player_ranks} table applied: the tested default of the backend-parity matrix. It proves the
 * default-absent read (a player with no pointer reads empty, the signal to default to the ladder's first rank),
 * the save → find round-trip carrying both the rank id and the prestige level, and that a re-save upserts on the
 * player uuid rather than inserting a second row (the second write's values win and the pointer stays single).
 */
class JooqPlayerRankRepositoryTest {

    private Persistence persistence;
    private JooqPlayerRankRepository repository;
    private UUID player;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqPlayerRankRepository(persistence.dsl(), Clock.systemUTC());
        player = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void findIsEmptyForAPlayerWithNoStoredRank() {
        assertThat(repository.find(player)).isEmpty();
    }

    @Test
    void savesAndFindsTheRankPointerRoundTrippingRankIdAndPrestige() {
        repository.save(player, RankId.of("citizen"), new Prestige(2));

        PlayerRank loaded = repository.find(player).orElseThrow();

        assertThat(loaded.rankId()).isEqualTo(RankId.of("citizen"));
        assertThat(loaded.prestige()).isEqualTo(new Prestige(2));
    }

    @Test
    void defaultsPrestigeIsStoredAndReadBack() {
        repository.save(player, RankId.of("first"), Prestige.INITIAL);

        PlayerRank loaded = repository.find(player).orElseThrow();

        assertThat(loaded.prestige().level()).isZero();
    }

    @Test
    void saveUpsertsOnThePlayerUuidRatherThanInserting() {
        repository.save(player, RankId.of("first"), Prestige.INITIAL);
        repository.save(player, RankId.of("vip"), new Prestige(1)); // same player, an advance, not a new row

        PlayerRank reloaded = repository.find(player).orElseThrow();
        assertThat(reloaded.rankId()).isEqualTo(RankId.of("vip"));
        assertThat(reloaded.prestige()).isEqualTo(new Prestige(1));
    }

    @Test
    void pointersAreScopedPerPlayer() {
        UUID other = UUID.randomUUID();
        repository.save(player, RankId.of("citizen"), Prestige.INITIAL);

        assertThat(repository.find(other)).isEmpty();
        assertThat(repository.find(player)).isPresent();
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
