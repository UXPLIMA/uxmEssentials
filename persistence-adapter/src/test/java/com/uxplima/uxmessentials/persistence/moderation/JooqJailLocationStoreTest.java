package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqJailLocationStore} against the default embedded SQLite backend with the
 * Flyway baseline applied (including the V8 jail-location table). The tested default of the backend-parity
 * matrix. It proves the round-trip (save → find) preserves the position, the name-key upsert (a re-anchor
 * overwrites in place rather than inserting), the {@code exists} check, the sorted {@code names} listing, and
 * the delete contract (true on the first removal, false on the second).
 */
class JooqJailLocationStoreTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqJailLocationStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqJailLocationStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndFindsAJailRoundTrip() {
        store.save(jail("spawnjail", 10, 64, 20, 90f, 45f));

        Optional<StoredJail> loaded = store.find("spawnjail");

        assertThat(loaded).isPresent();
        Position location = loaded.orElseThrow().location();
        assertThat(location.blockX()).isEqualTo(10);
        assertThat(location.blockZ()).isEqualTo(20);
        assertThat(location.yaw()).isEqualTo(90f);
        assertThat(location.pitch()).isEqualTo(45f);
        assertThat(location.world().name()).isEqualTo("world");
        assertThat(location.world().uid()).isEqualTo(WORLD.uid());
    }

    @Test
    void findFoldsTheLookupNameCase() {
        store.save(jail("mines", 0, 0, 0, 0f, 0f));

        assertThat(store.find("MINES")).isPresent();
    }

    @Test
    void saveUpsertsOnTheNameKeyRatherThanInserting() {
        store.save(jail("mines", 0, 0, 0, 0f, 0f));
        store.save(jail("mines", 100, 70, 100, 0f, 0f));

        assertThat(store.names()).containsExactly("mines");
        assertThat(store.find("mines").orElseThrow().location().blockX()).isEqualTo(100);
    }

    @Test
    void existsReflectsWhetherAJailIsStored() {
        assertThat(store.exists("spawnjail")).isFalse();

        store.save(jail("spawnjail", 0, 0, 0, 0f, 0f));

        assertThat(store.exists("spawnjail")).isTrue();
    }

    @Test
    void namesAreSorted() {
        store.save(jail("spawnjail", 0, 0, 0, 0f, 0f));
        store.save(jail("mines", 1, 1, 1, 0f, 0f));
        store.save(jail("arena", 2, 2, 2, 0f, 0f));

        assertThat(store.names()).containsExactly("arena", "mines", "spawnjail");
    }

    @Test
    void deleteReturnsTrueOnceThenFalse() {
        store.save(jail("spawnjail", 0, 0, 0, 0f, 0f));

        assertThat(store.delete("spawnjail")).isTrue();
        assertThat(store.delete("spawnjail")).isFalse();
        assertThat(store.exists("spawnjail")).isFalse();
    }

    private StoredJail jail(String name, double x, double y, double z, float yaw, float pitch) {
        return new StoredJail(name, new Position(WORLD, x, y, z, yaw, pitch));
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
