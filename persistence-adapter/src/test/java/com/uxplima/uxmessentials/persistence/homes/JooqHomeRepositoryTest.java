package com.uxplima.uxmessentials.persistence.homes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqHomeRepository} against the default embedded SQLite backend with all
 * Flyway migrations applied. It proves the round-trip (save → load), the {@code (owner, slot)} upsert (a
 * re-anchor overwrites in place rather than inserting), {@code findSlot}, {@code count}, {@code deleteSlot},
 * slot-ascending order, and the optional label/icon columns.
 */
class JooqHomeRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqHomeRepository repository;
    private PlayerRef owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqHomeRepository(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndLoadsAHomeRoundTrip() {
        repository.save(home(HomeSlot.of(0), 10, 64, 20));

        HomeSet loaded = repository.load(owner);

        assertThat(loaded.count()).isEqualTo(1);
        Home reloaded = loaded.find(HomeSlot.of(0)).orElseThrow();
        assertThat(reloaded.location().blockX()).isEqualTo(10);
        assertThat(reloaded.location().blockZ()).isEqualTo(20);
        assertThat(reloaded.location().world().name()).isEqualTo("world");
        assertThat(reloaded.slot()).isEqualTo(HomeSlot.of(0));
    }

    @Test
    void saveUpsertsOnTheOwnerSlotKeyRatherThanInserting() {
        repository.save(home(HomeSlot.of(0), 0, 0, 0));
        repository.save(home(HomeSlot.of(0), 100, 70, 100)); // same (owner, slot), a re-anchor

        assertThat(repository.count(owner)).isEqualTo(1);
        Home reanchored = repository.load(owner).find(HomeSlot.of(0)).orElseThrow();
        assertThat(reanchored.location().blockX()).isEqualTo(100);
    }

    @Test
    void countReflectsTheNumberOfHomes() {
        repository.save(home(HomeSlot.of(0), 0, 0, 0));
        repository.save(home(HomeSlot.of(1), 1, 1, 1));
        repository.save(home(HomeSlot.of(2), 2, 2, 2));

        assertThat(repository.count(owner)).isEqualTo(3);
    }

    @Test
    void findSlotReturnsTheHomeAtThatSlot() {
        repository.save(home(HomeSlot.of(0), 5, 64, 5));
        repository.save(home(HomeSlot.of(1), 10, 64, 10));

        Optional<Home> found = repository.findSlot(owner, HomeSlot.of(0));
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().location().blockX()).isEqualTo(5);

        assertThat(repository.findSlot(owner, HomeSlot.of(2))).isEmpty();
    }

    @Test
    void deleteSlotRemovesTheRow() {
        repository.save(home(HomeSlot.of(0), 0, 0, 0));
        repository.save(home(HomeSlot.of(1), 1, 1, 1));

        repository.deleteSlot(owner, HomeSlot.of(0));

        assertThat(repository.count(owner)).isEqualTo(1);
        assertThat(repository.findSlot(owner, HomeSlot.of(0))).isEmpty();
        assertThat(repository.findSlot(owner, HomeSlot.of(1))).isPresent();
    }

    @Test
    void loadPreservesSlotAscendingOrder() {
        repository.save(home(HomeSlot.of(2), 2, 64, 2));
        repository.save(home(HomeSlot.of(0), 0, 64, 0));
        repository.save(home(HomeSlot.of(1), 1, 64, 1));

        List<Home> all = repository.load(owner).all();
        assertThat(all.stream().map(h -> h.slot().index())).containsExactly(0, 1, 2);
    }

    @Test
    void persistsLabelAndIconRoundTrip() {
        Home withCosmetics = new Home(
                owner,
                HomeSlot.of(0),
                Position.of(WORLD, 5, 64, 5),
                Optional.of(HomeLabel.of("Base")),
                Optional.of(HomeIcon.of("GRASS_BLOCK")),
                false,
                Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(2_000));
        repository.save(withCosmetics);

        Home reloaded = repository.findSlot(owner, HomeSlot.of(0)).orElseThrow();
        assertThat(reloaded.label()).contains(HomeLabel.of("Base"));
        assertThat(reloaded.icon()).contains(HomeIcon.of("GRASS_BLOCK"));
        assertThat(reloaded.updatedAt()).isEqualTo(Instant.ofEpochMilli(2_000));
    }

    @Test
    void deleteAllRemovesEveryHomeForTheOwner() {
        repository.save(home(HomeSlot.of(0), 0, 64, 0));
        repository.save(home(HomeSlot.of(1), 1, 64, 1));

        repository.deleteAll(owner);

        assertThat(repository.count(owner)).isEqualTo(0);
        assertThat(repository.findSlot(owner, HomeSlot.of(0))).isEmpty();
        assertThat(repository.findSlot(owner, HomeSlot.of(1))).isEmpty();
    }

    @Test
    void absentLabelAndIconReadAsEmpty() {
        repository.save(home(HomeSlot.of(0), 5, 64, 5));

        Home reloaded = repository.findSlot(owner, HomeSlot.of(0)).orElseThrow();
        assertThat(reloaded.label()).isEmpty();
        assertThat(reloaded.icon()).isEmpty();
    }

    @Test
    void upsertUpdatesLabelAndIconOnConflict() {
        repository.save(home(HomeSlot.of(0), 5, 64, 5));

        Home updated = new Home(
                owner,
                HomeSlot.of(0),
                Position.of(WORLD, 5, 64, 5),
                Optional.of(HomeLabel.of("Camp")),
                Optional.of(HomeIcon.of("OAK_LOG")),
                false,
                Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(3_000));
        repository.save(updated);

        assertThat(repository.count(owner)).isEqualTo(1);
        Home reloaded = repository.findSlot(owner, HomeSlot.of(0)).orElseThrow();
        assertThat(reloaded.label()).contains(HomeLabel.of("Camp"));
        assertThat(reloaded.icon()).contains(HomeIcon.of("OAK_LOG"));
    }

    @Test
    void visibilityRoundTripPublicHome() {
        Home publicHome = new Home(
                owner,
                HomeSlot.of(0),
                Position.of(WORLD, 5, 64, 5),
                Optional.empty(),
                Optional.empty(),
                true,
                Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(1_000));
        repository.save(publicHome);

        Home reloaded = repository.findSlot(owner, HomeSlot.of(0)).orElseThrow();
        assertThat(reloaded.isPublic()).isTrue();
    }

    @Test
    void visibilityDefaultsToPrivate() {
        repository.save(home(HomeSlot.of(0), 0, 64, 0));

        Home reloaded = repository.findSlot(owner, HomeSlot.of(0)).orElseThrow();
        assertThat(reloaded.isPublic()).isFalse();
    }

    @Test
    void upsertUpdatesVisibility() {
        repository.save(home(HomeSlot.of(0), 0, 64, 0)); // private by default
        Home madePublic = new Home(
                owner,
                HomeSlot.of(0),
                Position.of(WORLD, 0, 64, 0),
                Optional.empty(),
                Optional.empty(),
                true,
                Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(2_000));
        repository.save(madePublic);

        assertThat(repository.count(owner)).isEqualTo(1);
        assertThat(repository.findSlot(owner, HomeSlot.of(0)).orElseThrow().isPublic())
                .isTrue();
    }

    private Home home(HomeSlot slot, double x, double y, double z) {
        return new Home(
                owner,
                slot,
                Position.of(WORLD, x, y, z),
                Optional.empty(),
                Optional.empty(),
                false,
                Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(1_000));
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
