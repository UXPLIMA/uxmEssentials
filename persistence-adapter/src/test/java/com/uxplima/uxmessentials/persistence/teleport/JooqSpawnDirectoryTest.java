package com.uxplima.uxmessentials.persistence.teleport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqSpawnDirectory} against the default embedded SQLite backend with the
 * Flyway V1-V10 migrations applied. It proves the per-world spawn round-trip and key upsert, the singleton
 * main-spawn upsert (a second {@code /setmainspawn} overwrites id 0 rather than inserting), the per-world
 * remove contract (true once, then false), the named-spawn round-trip with case-folded lookup, and the mirror
 * persist-and-load. The store carries no vanilla fallback, so an unset spawn reads as empty here.
 */
class JooqSpawnDirectoryTest {

    private static final WorldRef OVERWORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef NETHER = new WorldRef(UUID.randomUUID(), "world_nether");

    private Persistence persistence;
    private JooqSpawnDirectory store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqSpawnDirectory(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void unsetPerWorldSpawnReadsAsEmpty() {
        assertThat(store.operatorSpawn(OVERWORLD)).isEmpty();
        assertThat(store.defaultSpawn(OVERWORLD)).isEmpty();
    }

    @Test
    void perWorldSpawnRoundTrips() {
        store.setDefaultSpawn(OVERWORLD, at(OVERWORLD, 10, 64, 20, 90f, 45f));

        Optional<Position> loaded = store.operatorSpawn(OVERWORLD);

        assertThat(loaded).isPresent();
        Position spawn = loaded.orElseThrow();
        assertThat(spawn.blockX()).isEqualTo(10);
        assertThat(spawn.blockZ()).isEqualTo(20);
        assertThat(spawn.yaw()).isEqualTo(90f);
        assertThat(spawn.world().uid()).isEqualTo(OVERWORLD.uid());
    }

    @Test
    void perWorldSpawnUpsertsOnTheWorldKey() {
        store.setDefaultSpawn(OVERWORLD, at(OVERWORLD, 0, 0, 0, 0f, 0f));
        store.setDefaultSpawn(OVERWORLD, at(OVERWORLD, 100, 70, 100, 0f, 0f));

        assertThat(store.operatorSpawn(OVERWORLD).orElseThrow().blockX()).isEqualTo(100);
    }

    @Test
    void removeReturnsTrueOnceThenFalse() {
        store.setDefaultSpawn(NETHER, at(NETHER, 1, 1, 1, 0f, 0f));

        assertThat(store.removeDefaultSpawn(NETHER)).isTrue();
        assertThat(store.removeDefaultSpawn(NETHER)).isFalse();
        assertThat(store.operatorSpawn(NETHER)).isEmpty();
    }

    @Test
    void mainSpawnIsASingletonThatUpserts() {
        assertThat(store.mainSpawn()).isEmpty();

        store.setMainSpawn(at(OVERWORLD, 5, 65, 5, 0f, 0f));
        store.setMainSpawn(at(NETHER, 7, 67, 7, 0f, 0f));

        Optional<Position> main = store.mainSpawn();
        assertThat(main).isPresent();
        assertThat(main.orElseThrow().blockX()).isEqualTo(7);
        assertThat(main.orElseThrow().world().uid()).isEqualTo(NETHER.uid());
    }

    @Test
    void namedSpawnRoundTripsWithCaseFoldedLookup() {
        store.setNamedSpawn("Hub", at(OVERWORLD, 3, 63, 3, 0f, 0f));

        assertThat(store.namedSpawn("hub")).isPresent();
        assertThat(store.namedSpawn("HUB").orElseThrow().blockX()).isEqualTo(3);
    }

    @Test
    void mirrorPersistsAndLoads() {
        store.setMirror(new SpawnMirror(OVERWORLD.uid(), NETHER.uid()));

        Optional<SpawnMirror> loaded = store.mirrorFor(OVERWORLD);
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().sourceWorld()).isEqualTo(OVERWORLD.uid());
        assertThat(loaded.orElseThrow().targetWorld()).isEqualTo(NETHER.uid());
    }

    @Test
    void mirrorUpsertsOnTheSourceWorld() {
        store.setMirror(new SpawnMirror(OVERWORLD.uid(), NETHER.uid()));
        WorldRef end = new WorldRef(UUID.randomUUID(), "world_the_end");
        store.setMirror(new SpawnMirror(OVERWORLD.uid(), end.uid()));

        assertThat(store.mirrorFor(OVERWORLD).orElseThrow().targetWorld()).isEqualTo(end.uid());
    }

    private static Position at(WorldRef world, double x, double y, double z, float yaw, float pitch) {
        return new Position(world, x, y, z, yaw, pitch);
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
