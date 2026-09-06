package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
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
 * End-to-end coverage of {@link JooqPlayerWarpPasswordStore} against the default embedded SQLite backend with the
 * Flyway V1-V70 migrations applied and a real {@link Pbkdf2PasswordHasher}. Each case seeds a genuine warp row
 * through {@link JooqPlayerWarpRepository#save} (so every NOT-NULL column is filled and the surrogate id is real),
 * then proves the set/clear/match lifecycle: a set password verifies and a wrong candidate does not, the stored
 * digest is never the plaintext, a second set overwrites, a clear nulls the three columns and stops matching, and
 * a warp that never had a password answers {@code matches} with {@code false} rather than throwing.
 */
class JooqPlayerWarpPasswordStoreTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final Position POSITION = new Position(WORLD, 12.5, 64.0, -30.5, 90.0f, -12.0f);

    private Persistence persistence;
    private JooqPlayerWarpRepository repo;
    private JooqPlayerWarpPasswordStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repo = new JooqPlayerWarpRepository(persistence.dsl());
        store = new JooqPlayerWarpPasswordStore(persistence.dsl(), new Pbkdf2PasswordHasher());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void aSetPasswordVerifiesAndAWrongCandidateDoesNot() {
        PlayerWarpId id = seedWarp("hut");

        store.set(id, "hunter2");

        assertThat(store.matches(id, "hunter2")).isTrue();
        assertThat(store.matches(id, "wrong")).isFalse();
    }

    @Test
    void setStoresADigestNotThePlaintextAndCarriesTheAlgorithm() {
        PlayerWarpId id = seedWarp("hut");

        store.set(id, "hunter2");

        PlayerWarpsRecord row = row(id);
        assertThat(row.getPasswordHash()).as("only the digest is stored").isNotEqualTo("hunter2");
        assertThat(row.getPasswordAlgorithm()).isEqualTo("PBKDF2WithHmacSHA256");
        assertThat(row.getPasswordSalt()).isNotBlank();
        assertThat(row.getPasswordHash()).isNotBlank();
    }

    @Test
    void settingAgainOverwritesThePassword() {
        PlayerWarpId id = seedWarp("hut");

        store.set(id, "hunter2");
        store.set(id, "correct-horse");

        assertThat(store.matches(id, "hunter2")).isFalse();
        assertThat(store.matches(id, "correct-horse")).isTrue();
    }

    @Test
    void clearRemovesThePasswordAndNullsTheColumns() {
        PlayerWarpId id = seedWarp("hut");
        store.set(id, "hunter2");

        store.clear(id);

        assertThat(store.matches(id, "hunter2")).isFalse();
        PlayerWarpsRecord row = row(id);
        assertThat(row.getPasswordAlgorithm()).isNull();
        assertThat(row.getPasswordSalt()).isNull();
        assertThat(row.getPasswordHash()).isNull();
    }

    @Test
    void matchesIsFalseWhenNoPasswordIsSet() {
        PlayerWarpId id = seedWarp("hut");

        assertThat(store.matches(id, "anything")).isFalse();
    }

    private PlayerWarpId seedWarp(String name) {
        PlayerWarp warp =
                PlayerWarp.create(OWNER, OWNER.name(), PlayerWarpName.of(name), POSITION, Instant.ofEpochMilli(1_000L));
        return repo.save(warp);
    }

    private PlayerWarpsRecord row(PlayerWarpId id) {
        PlayerWarpsRecord row = persistence
                .dsl()
                .selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .fetchOne();
        assertThat(row).isNotNull();
        return row;
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
