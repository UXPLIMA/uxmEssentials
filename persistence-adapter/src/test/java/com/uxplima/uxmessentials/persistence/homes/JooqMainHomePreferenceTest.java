package com.uxplima.uxmessentials.persistence.homes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
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
 * Asserts the V22 migration produces the expected final schema shape: the {@code homes} table has the
 * slot-keyed structure with nullable label, icon, and updated_at columns, and the {@code home_main} table
 * has been dropped. Verified by round-tripping data through the slot-keyed repository and confirming
 * queries against {@code home_main} fail (the table does not exist).
 */
class JooqMainHomePreferenceTest {

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
    void homesTableHasSlotKeyedShapeAfterMigration() {
        // A home with all new columns round-trips cleanly: the schema accepted slot, label, icon, updated_at.
        Home home = new Home(
                owner,
                HomeSlot.of(0),
                Position.of(WORLD, 10, 64, 20),
                Optional.of(HomeLabel.of("Base")),
                Optional.of(HomeIcon.of("GRASS_BLOCK")),
                false,
                Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(2_000));

        assertThatCode(() -> repository.save(home)).doesNotThrowAnyException();

        Home reloaded = repository.findSlot(owner, HomeSlot.of(0)).orElseThrow();
        assertThat(reloaded.slot()).isEqualTo(HomeSlot.of(0));
        assertThat(reloaded.label()).contains(HomeLabel.of("Base"));
        assertThat(reloaded.icon()).contains(HomeIcon.of("GRASS_BLOCK"));
        assertThat(reloaded.updatedAt()).isEqualTo(Instant.ofEpochMilli(2_000));
    }

    @Test
    void homeMainTableIsAbsentAfterMigration() {
        // A direct query against home_main must fail because V22 drops the table.
        assertThatThrownBy(() -> persistence.dsl().execute("SELECT owner FROM home_main LIMIT 1"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void multipleSlotsSavedAndLoadedSlotAscending() {
        repository.save(slot(2, 30));
        repository.save(slot(0, 10));
        repository.save(slot(1, 20));

        List<Home> all = repository.load(owner).all();
        assertThat(all.stream().map(h -> h.slot().index())).containsExactly(0, 1, 2);
        assertThat(all.stream().map(h -> (int) h.location().x())).containsExactly(10, 20, 30);
    }

    private Home slot(int slotIndex, double x) {
        return new Home(
                owner,
                HomeSlot.of(slotIndex),
                Position.of(WORLD, x, 64, x),
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
