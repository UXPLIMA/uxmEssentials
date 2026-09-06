package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqPendingTeleportStore} against the default embedded SQLite backend with the
 * Flyway V1-V70 migrations applied. It proves the store's contract over the {@code player_warp_pending_teleports}
 * table: a recorded row round-trips every field (with the charged amount preserved to the currency's scale), a
 * record with no charge round-trips an empty {@code paid}, a second record for the same player upserts on the
 * primary key rather than inserting a duplicate, a clear drops the row, and an unknown player answers empty.
 */
class JooqPendingTeleportStoreTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Persistence persistence;
    private PendingTeleportStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = PlayerWarpRepositories.pendingTeleportStore(persistence);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void aRecordedTeleportRoundTripsEveryField() {
        PendingTeleport recorded = new PendingTeleport(
                PLAYER,
                PlayerWarpId.of(42L),
                "survival",
                "lobby",
                Instant.ofEpochMilli(1_700_000_000_000L),
                Optional.of(WarpCost.of(new BigDecimal("250.50"), "coins")));

        store.record(recorded);

        PendingTeleport found = store.find(PLAYER).orElseThrow();
        assertThat(found.player()).isEqualTo(PLAYER);
        assertThat(found.warp()).isEqualTo(PlayerWarpId.of(42L));
        assertThat(found.targetServer()).isEqualTo("survival");
        assertThat(found.originServer()).isEqualTo("lobby");
        assertThat(found.requestedAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(found.paid()).isPresent();
        assertThat(found.paid().orElseThrow().currencyId()).isEqualTo("coins");
        assertThat(found.paid().orElseThrow().amount()).isEqualByComparingTo("250.50");
    }

    @Test
    void aRecordWithNoChargeRoundTripsAnEmptyPaid() {
        store.record(new PendingTeleport(
                PLAYER, PlayerWarpId.of(7L), "survival", "lobby", Instant.ofEpochMilli(1_000L), Optional.empty()));

        assertThat(store.find(PLAYER).orElseThrow().paid()).isEmpty();
    }

    @Test
    void asecondRecordUpsertsOnThePlayerKey() {
        store.record(new PendingTeleport(
                PLAYER,
                PlayerWarpId.of(7L),
                "survival",
                "lobby",
                Instant.ofEpochMilli(1_000L),
                Optional.of(WarpCost.of(new BigDecimal("10"), "default"))));

        store.record(new PendingTeleport(
                PLAYER, PlayerWarpId.of(99L), "creative", "lobby", Instant.ofEpochMilli(2_000L), Optional.empty()));

        PendingTeleport found = store.find(PLAYER).orElseThrow();
        assertThat(found.warp()).isEqualTo(PlayerWarpId.of(99L));
        assertThat(found.targetServer()).isEqualTo("creative");
        assertThat(found.paid()).as("the overwriting row carried no charge").isEmpty();
    }

    @Test
    void clearRemovesTheRow() {
        store.record(new PendingTeleport(
                PLAYER, PlayerWarpId.of(7L), "survival", "lobby", Instant.ofEpochMilli(1_000L), Optional.empty()));

        store.clear(PLAYER);

        assertThat(store.find(PLAYER)).isEmpty();
    }

    @Test
    void findForAnUnknownPlayerIsEmpty() {
        assertThat(store.find(UUID.randomUUID())).isEmpty();
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
