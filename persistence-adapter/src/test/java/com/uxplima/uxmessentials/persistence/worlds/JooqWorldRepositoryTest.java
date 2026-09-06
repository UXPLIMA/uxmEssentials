package com.uxplima.uxmessentials.persistence.worlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWorldRepository} against the default embedded SQLite backend with the
 * Flyway V61 baseline applied. It proves the round-trip (save → find) of a managed world with its optional
 * spec fields and known uid, the name-key upsert (a re-save overwrites in place rather than inserting), and
 * the delete dropping the row so {@code exists} reports it absent.
 */
class JooqWorldRepositoryTest {

    private Persistence persistence;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    private WorldRepository newRepository() {
        return new JooqWorldRepository(persistence.dsl());
    }

    @Test
    void roundTripsAManagedWorldWithOptionalsAndUid() {
        WorldRepository repo = newRepository();
        UUID uid = UUID.randomUUID();
        WorldSpec spec = new WorldSpec(
                WorldEnvironment.NETHER,
                WorldGenType.FLAT,
                Optional.of(99L),
                Optional.empty(),
                false,
                Optional.empty());
        ManagedWorld world = ManagedWorld.created(
                        WorldName.of("creative"),
                        spec,
                        true,
                        Optional.of(UUID.randomUUID()),
                        Instant.ofEpochMilli(1234))
                .withKnownUid(uid);

        repo.save(world);
        Optional<ManagedWorld> found = repo.find(WorldName.of("creative"));

        assertThat(found).isPresent();
        ManagedWorld got = found.orElseThrow();
        assertThat(got.spec().environment()).isEqualTo(WorldEnvironment.NETHER);
        assertThat(got.spec().worldType()).isEqualTo(WorldGenType.FLAT);
        assertThat(got.spec().seed()).hasValue(99L);
        assertThat(got.spec().generateStructures()).isFalse();
        assertThat(got.autoLoad()).isTrue();
        assertThat(got.knownUid()).hasValue(uid);
        assertThat(got.createdAt()).isEqualTo(Instant.ofEpochMilli(1234));
    }

    @Test
    void saveUpsertsAndDeleteRemoves() {
        WorldRepository repo = newRepository();
        ManagedWorld w =
                ManagedWorld.created(WorldName.of("w"), WorldSpec.normal(), false, Optional.empty(), Instant.EPOCH);
        repo.save(w);
        repo.save(w.withAutoLoad(true)); // upsert
        assertThat(repo.find(WorldName.of("w")).orElseThrow().autoLoad()).isTrue();
        repo.delete(WorldName.of("w"));
        assertThat(repo.exists(WorldName.of("w"))).isFalse();
    }

    @Test
    void roundTripsSettingsWithTheAggregate() {
        WorldRepository repo = newRepository();
        var w = ManagedWorld.created(
                        WorldName.of("creative"),
                        WorldSpec.normal(),
                        true,
                        java.util.Optional.empty(),
                        java.time.Instant.EPOCH)
                .withSettings(WorldSettings.defaults()
                        .with(WorldProperties.PVP, false)
                        .withRaw("gamerule.keepInventory", "true")
                        .withRaw("spawn", "10;64;20;0.0;0.0"));
        repo.save(w);

        var found = repo.find(WorldName.of("creative")).orElseThrow();
        assertThat(found.settings().get(WorldProperties.PVP)).isFalse();
        assertThat(found.settings().gamerules()).containsEntry("keepInventory", "true");
        assertThat(found.settings().spawn()).contains("10;64;20;0.0;0.0");
    }

    @Test
    void saveReplacesSettingsAndDeleteRemovesThem() {
        WorldRepository repo = newRepository();
        var w = ManagedWorld.created(
                        WorldName.of("w"),
                        WorldSpec.normal(),
                        false,
                        java.util.Optional.empty(),
                        java.time.Instant.EPOCH)
                .withSettings(WorldSettings.defaults().with(WorldProperties.PVP, false));
        repo.save(w);
        repo.save(repo.find(WorldName.of("w")).orElseThrow().withSettings(WorldSettings.defaults())); // clear
        assertThat(repo.find(WorldName.of("w")).orElseThrow().settings().raw()).isEmpty();
        repo.delete(WorldName.of("w"));
        assertThat(repo.exists(WorldName.of("w"))).isFalse();
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
