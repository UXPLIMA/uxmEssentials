package com.uxplima.uxmessentials.persistence.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqPlayerDataRepository} against the default embedded SQLite backend with the
 * Flyway ladder applied (the tested default of the backend-parity matrix; the network backends run the same DSL
 * behind Testcontainers, which this environment may not have). It proves the upsert inserts then overwrites on the
 * composite {@code (uuid, data_key)} key, that {@code delete} removes a single row, that {@code loadAll} returns a
 * player's whole key set, and that one player's rows never bleed into another's.
 */
class JooqPlayerDataRepositoryTest {

    private Persistence persistence;
    private JooqPlayerDataRepository repository;
    private UUID player;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqPlayerDataRepository(persistence.dsl());
        player = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void upsertInsertsThenLoadReadsTheValueBack() {
        repository.upsert(player, "rank", "gold");

        assertThat(repository.loadAll(player)).containsExactly(entry("rank", "gold"));
    }

    @Test
    void upsertOverwritesAnExistingKeyRatherThanDuplicatingIt() {
        repository.upsert(player, "rank", "gold");
        repository.upsert(player, "rank", "diamond");

        assertThat(repository.loadAll(player)).containsExactly(entry("rank", "diamond"));
    }

    @Test
    void loadAllReturnsEveryKeyForThePlayer() {
        repository.upsert(player, "rank", "gold");
        repository.upsert(player, "coins", "42");

        Map<String, String> all = repository.loadAll(player);

        assertThat(all).containsOnly(entry("rank", "gold"), entry("coins", "42"));
    }

    @Test
    void deleteRemovesOnlyTheNamedKey() {
        repository.upsert(player, "rank", "gold");
        repository.upsert(player, "coins", "42");

        repository.delete(player, "rank");

        assertThat(repository.loadAll(player)).containsExactly(entry("coins", "42"));
    }

    @Test
    void loadAllOfAnUntrackedPlayerIsEmpty() {
        assertThat(repository.loadAll(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rowsAreIsolatedPerPlayer() {
        UUID other = UUID.randomUUID();
        repository.upsert(player, "rank", "gold");
        repository.upsert(other, "rank", "stone");

        repository.delete(player, "rank");

        assertThat(repository.loadAll(player)).isEmpty();
        assertThat(repository.loadAll(other)).containsExactly(entry("rank", "stone"));
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
