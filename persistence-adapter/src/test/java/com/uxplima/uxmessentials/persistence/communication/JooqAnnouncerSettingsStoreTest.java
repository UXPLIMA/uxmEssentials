package com.uxplima.uxmessentials.persistence.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.uxplima.uxmessentials.communication.domain.AnnouncerSettings;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqAnnouncerSettingsStore} against the default embedded SQLite backend with the
 * Flyway ladder applied through V66. It proves the seeded row reads as fully unset on a fresh database, that saving
 * an interval and a gate round-trips, that clearing a value back to the sentinel reads as unset, and that a re-save
 * updates the single row in place rather than inserting a second.
 */
class JooqAnnouncerSettingsStoreTest {

    private Persistence persistence;
    private JooqAnnouncerSettingsStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqAnnouncerSettingsStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void aFreshDatabaseReadsAsFullyUnset() {
        AnnouncerSettings loaded = store.load();

        assertThat(loaded.interval()).isEmpty();
        assertThat(loaded.minOnlinePlayers()).isEmpty();
    }

    @Test
    void savingAnIntervalAndGateRoundTrips() {
        store.save(AnnouncerSettings.unset().withIntervalSeconds(150).withMinOnlinePlayers(3));

        AnnouncerSettings loaded = store.load();
        assertThat(loaded.interval()).contains(Duration.ofSeconds(150));
        assertThat(loaded.minOnlinePlayers()).hasValue(3);
    }

    @Test
    void clearingAValueReadsAsUnset() {
        store.save(AnnouncerSettings.unset().withIntervalSeconds(90).withMinOnlinePlayers(5));

        store.save(store.load().withIntervalSeconds(0)); // a non-positive interval clears it

        AnnouncerSettings loaded = store.load();
        assertThat(loaded.interval()).isEmpty();
        assertThat(loaded.minOnlinePlayers()).hasValue(5); // the gate is untouched
    }

    @Test
    void aReSaveUpdatesTheSingleRowInPlace() {
        store.save(AnnouncerSettings.unset().withIntervalSeconds(45));
        store.save(AnnouncerSettings.unset().withIntervalSeconds(200));

        assertThat(store.load().interval()).contains(Duration.ofSeconds(200));
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

    /** A logger that drops everything: the store test asserts on rows, not log output. */
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
