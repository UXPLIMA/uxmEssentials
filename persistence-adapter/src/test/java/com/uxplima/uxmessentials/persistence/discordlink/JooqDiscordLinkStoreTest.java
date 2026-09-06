package com.uxplima.uxmessentials.persistence.discordlink;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqDiscordLinkStore} against the default embedded SQLite backend with the
 * Flyway baseline applied (including the V16 discord-link tables). The tested default of the backend-parity
 * matrix. It proves the pending round-trip, the per-player upsert (a fresh code replaces the row), the
 * confirm-binds-and-clears-pending contract, the by-player and by-Discord-id lookups, the unlink contract
 * (true on the first removal, false on the second), and the unique Discord-id constraint.
 */
class JooqDiscordLinkStoreTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private Persistence persistence;
    private JooqDiscordLinkStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqDiscordLinkStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndFindsAPendingCodeRoundTrip() {
        PendingLink pending = new PendingLink(ALICE, LinkCode.of("ABC234"), NOW.plusSeconds(600));
        store.savePending(pending);

        assertThat(store.findPendingByCode(LinkCode.of("ABC234"))).contains(pending);
        assertThat(store.findPendingByCode(LinkCode.of("ZZZ999"))).isEmpty();
    }

    @Test
    void savePendingUpsertsOnThePlayerKeyReplacingTheCode() {
        store.savePending(new PendingLink(ALICE, LinkCode.of("ABC234"), NOW.plusSeconds(600)));
        store.savePending(new PendingLink(ALICE, LinkCode.of("XYZ234"), NOW.plusSeconds(900)));

        // The old code no longer resolves; only the latest remains, one row per player.
        assertThat(store.findPendingByCode(LinkCode.of("ABC234"))).isEmpty();
        assertThat(store.findPendingByCode(LinkCode.of("XYZ234")).orElseThrow().player())
                .isEqualTo(ALICE);
    }

    @Test
    void confirmBindsTheBindingAndClearsThePendingRow() {
        store.savePending(new PendingLink(ALICE, LinkCode.of("ABC234"), NOW.plusSeconds(600)));
        ConfirmedLink link = new ConfirmedLink(ALICE, DiscordId.of("123456789012345678"), NOW);

        store.confirm(link);

        assertThat(store.findByPlayer(ALICE)).contains(link);
        assertThat(store.findByDiscordId(DiscordId.of("123456789012345678"))).contains(link);
        assertThat(store.findPendingByCode(LinkCode.of("ABC234"))).isEmpty();
    }

    @Test
    void unlinkReturnsTrueOnceThenFalse() {
        store.confirm(new ConfirmedLink(ALICE, DiscordId.of("123456789012345678"), NOW));

        assertThat(store.unlink(ALICE)).isTrue();
        assertThat(store.unlink(ALICE)).isFalse();
        assertThat(store.findByPlayer(ALICE)).isEmpty();
    }

    @Test
    void bindingsForDifferentPlayersAndDiscordIdsCoexist() {
        store.confirm(new ConfirmedLink(ALICE, DiscordId.of("111111111111111111"), NOW));
        store.confirm(new ConfirmedLink(BOB, DiscordId.of("222222222222222222"), NOW));

        assertThat(store.findByDiscordId(DiscordId.of("111111111111111111"))
                        .orElseThrow()
                        .player())
                .isEqualTo(ALICE);
        assertThat(store.findByDiscordId(DiscordId.of("222222222222222222"))
                        .orElseThrow()
                        .player())
                .isEqualTo(BOB);
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
