package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWarpBanStore} against the default embedded SQLite backend with the Flyway
 * V1-V70 migrations applied. It proves a permanent and a timed ban round-trip field-for-field (the optional
 * expiry, reason, and imposer surviving as present or absent), that {@link JooqWarpBanStore#isBannedAt} honours
 * the timed expiry, that re-banning upserts one row rather than inserting a second, and that an unban clears the
 * record.
 */
class JooqWarpBanStoreTest {

    private static final PlayerWarpId WARP = PlayerWarpId.of(1L);
    private static final Instant BANNED_AT = Instant.ofEpochMilli(1_000L);

    private Persistence persistence;
    private JooqWarpBanStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqWarpBanStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void aPermanentBanRoundTripsWithAnAbsentExpiry() {
        UUID player = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        BanRecord permanent =
                new BanRecord(player, Optional.empty(), Optional.of("griefing"), Optional.of(staff), BANNED_AT);

        store.ban(WARP, permanent);

        BanRecord loaded = store.find(WARP, player).orElseThrow();
        assertThat(loaded.player()).isEqualTo(player);
        assertThat(loaded.until()).isEmpty();
        assertThat(loaded.reason()).contains("griefing");
        assertThat(loaded.bannedBy()).contains(staff);
        assertThat(loaded.bannedAt()).isEqualTo(BANNED_AT);
    }

    @Test
    void aReasonlessConsoleBanRoundTripsWithAbsentOptionals() {
        UUID player = UUID.randomUUID();
        BanRecord bare = new BanRecord(player, Optional.empty(), Optional.empty(), Optional.empty(), BANNED_AT);

        store.ban(WARP, bare);

        BanRecord loaded = store.find(WARP, player).orElseThrow();
        assertThat(loaded.reason()).isEmpty();
        assertThat(loaded.bannedBy()).isEmpty();
    }

    @Test
    void aTimedBanIsActiveBeforeItsExpiryAndInactiveAfter() {
        UUID player = UUID.randomUUID();
        Instant until = Instant.ofEpochMilli(5_000L);
        store.ban(WARP, new BanRecord(player, Optional.of(until), Optional.empty(), Optional.empty(), BANNED_AT));

        assertThat(store.find(WARP, player).orElseThrow().until()).contains(until);
        assertThat(store.isBannedAt(WARP, player, Instant.ofEpochMilli(4_999L))).isTrue();
        assertThat(store.isBannedAt(WARP, player, until)).isFalse();
        assertThat(store.isBannedAt(WARP, player, Instant.ofEpochMilli(5_001L))).isFalse();
    }

    @Test
    void isBannedAtIsFalseForAPlayerWhoIsNotBanned() {
        assertThat(store.isBannedAt(WARP, UUID.randomUUID(), BANNED_AT)).isFalse();
    }

    @Test
    void reBanningTheSamePlayerUpsertsTheReasonAsOneRow() {
        UUID player = UUID.randomUUID();
        store.ban(WARP, new BanRecord(player, Optional.empty(), Optional.of("first"), Optional.empty(), BANNED_AT));

        store.ban(WARP, new BanRecord(player, Optional.empty(), Optional.of("second"), Optional.empty(), BANNED_AT));

        assertThat(store.list(WARP)).hasSize(1);
        assertThat(store.find(WARP, player).orElseThrow().reason()).contains("second");
    }

    @Test
    void unbanningRemovesTheRecord() {
        UUID player = UUID.randomUUID();
        store.ban(WARP, new BanRecord(player, Optional.empty(), Optional.empty(), Optional.empty(), BANNED_AT));

        store.unban(WARP, player);

        assertThat(store.find(WARP, player)).isEmpty();
        assertThat(store.list(WARP)).isEmpty();
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
