package com.uxplima.uxmessentials.persistence.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqTrustStore} against the embedded SQLite backend with the Flyway V77
 * {@code security_trusted} table applied: a trusted device is recognised until its expiry lapses, a different device
 * or player is not trusted, and re-trusting the same device slides the expiry forward.
 */
class JooqTrustStoreTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String IP_A = "hash-of-ip-a";
    private static final String IP_B = "hash-of-ip-b";

    private Persistence persistence;
    private TrustStore store;
    private UUID player;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqTrustStore(persistence.dsl());
        player = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void anUntrustedDeviceIsNotTrusted() {
        assertThat(store.isTrusted(player, IP_A, NOW)).isFalse();
    }

    @Test
    void aTrustedDeviceIsRecognisedUntilItExpires() {
        store.trust(player, IP_A, NOW.plusSeconds(3600));

        assertThat(store.isTrusted(player, IP_A, NOW)).isTrue();
        // A different device and a different player share no trust.
        assertThat(store.isTrusted(player, IP_B, NOW)).isFalse();
        assertThat(store.isTrusted(UUID.randomUUID(), IP_A, NOW)).isFalse();
        // Past the expiry the trust lapses.
        assertThat(store.isTrusted(player, IP_A, NOW.plusSeconds(7200))).isFalse();
    }

    @Test
    void reTrustingTheSameDeviceSlidesTheExpiryForward() {
        store.trust(player, IP_A, NOW.plusSeconds(60));
        store.trust(player, IP_A, NOW.plusSeconds(7200));

        assertThat(store.isTrusted(player, IP_A, NOW.plusSeconds(3600))).isTrue();
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
