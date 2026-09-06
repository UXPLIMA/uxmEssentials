package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqModerationRepository} against the default embedded SQLite backend with the
 * Flyway V5 moderation tables applied. It proves each sanction kind round-trips its shape (permanent vs timed
 * mute, online-only vs wall-clock jail, tempban), that a {@code None} save deletes the row, that the warn
 * history is append-only and read newest-first, that an IP ban with a stored target is queryable and
 * removable, and that the seen record keeps each account's last address. These are the durable facts the
 * {@code MutePolicy}, {@code JailGate} and login enforcement read; the address associations behind alt detection
 * live in the kernel {@code ip_history} table instead (see {@code JooqIpHistoryStoreTest}).
 */
class JooqModerationRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final Issuer STAFF = Issuer.console("admin");

    private Persistence persistence;
    private JooqModerationRepository repository;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqModerationRepository(persistence.dsl());
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void timedMuteRoundTripsAndUnmuteDeletes() {
        repository.saveMute(alice, MuteState.timed(T0.plus(Duration.ofHours(1)), STAFF, Optional.of("spam"), T0));

        MuteState loaded = repository.loadMute(alice);
        assertThat(loaded).isInstanceOf(MuteState.Timed.class);
        assertThat(loaded.expiry()).contains(T0.plus(Duration.ofHours(1)));

        repository.saveMute(alice, MuteState.none());
        assertThat(repository.loadMute(alice)).isInstanceOf(MuteState.None.class);
    }

    @Test
    void onlineOnlyJailRoundTripsItsRemainder() {
        repository.saveJail(alice, JailState.onlineTimed("cells", Duration.ofMinutes(10), STAFF, Optional.empty(), T0));

        JailState loaded = repository.loadJail(alice);
        assertThat(loaded).isInstanceOf(JailState.Active.class);
        JailState.Active active = (JailState.Active) loaded;
        assertThat(active.isOnlineTimed()).isTrue();
        assertThat(active.remaining()).contains(Duration.ofMinutes(10));
        assertThat(active.until()).isEmpty();
    }

    @Test
    void wallClockJailRoundTripsItsExpiry() {
        Instant until = T0.plus(Duration.ofHours(3));
        repository.saveJail(alice, JailState.wallClockTimed("cells", until, STAFF, Optional.empty(), T0));

        JailState.Active active = (JailState.Active) repository.loadJail(alice);
        assertThat(active.until()).contains(until);
        assertThat(active.remaining()).isEmpty();
    }

    @Test
    void tempbanRoundTripsItsExpiry() {
        Instant until = T0.plus(Duration.ofDays(2));
        repository.saveTempban(alice, TempbanState.active(until, STAFF, Optional.of("cheat"), T0));

        TempbanState loaded = repository.loadTempban(alice);
        assertThat(loaded).isInstanceOf(TempbanState.Active.class);
        assertThat(loaded.expiry()).contains(until);
    }

    @Test
    void warnHistoryIsAppendOnlyAndReadNewestFirst() {
        assertThat(repository.appendWarn(alice, Warn.standing(STAFF, Optional.of("first"), T0)))
                .isEqualTo(1);
        assertThat(repository.appendWarn(alice, Warn.standing(STAFF, Optional.of("second"), T0.plusSeconds(60))))
                .isEqualTo(2);

        List<Warn> history = repository.warns(alice, T0.plusSeconds(120));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).reason()).contains("second");
        assertThat(history.get(1).reason()).contains("first");
    }

    @Test
    void tempWarnLapsesOutOfTheReadAndCountOnceItsExpiryPasses() {
        Instant expiry = T0.plusSeconds(60);
        assertThat(repository.appendWarn(alice, Warn.standing(STAFF, Optional.of("standing"), T0)))
                .isEqualTo(1);
        assertThat(repository.appendWarn(alice, Warn.timed(STAFF, Optional.of("timed"), T0, expiry)))
                .isEqualTo(2);

        // Before the expiry both count; the row stays in the append-only table either way.
        assertThat(repository.warns(alice, T0.plusSeconds(30))).hasSize(2);
        // After the expiry only the standing warning is still in effect.
        List<Warn> remaining = repository.warns(alice, expiry.plusSeconds(1));
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).reason()).contains("standing");
        assertThat(repository.clearWarns(alice)).isEqualTo(2);
    }

    @Test
    void clearWarnsRemovesEveryRowAndReportsTheCount() {
        repository.appendWarn(alice, Warn.standing(STAFF, Optional.of("first"), T0));
        repository.appendWarn(alice, Warn.standing(STAFF, Optional.of("second"), T0.plusSeconds(60)));

        assertThat(repository.clearWarns(alice)).isEqualTo(2);
        assertThat(repository.warns(alice, T0.plusSeconds(120))).isEmpty();
        assertThat(repository.clearWarns(alice)).isZero();
    }

    @Test
    void ipBanRoundTripsTargetAndIsRemovable() {
        IpBan ban = new IpBan(
                "203.0.113.7", Optional.empty(), Optional.of("evasion"), Optional.of(alice.uuid()), STAFF, T0);
        repository.saveIpBan(ban);

        Optional<IpBan> loaded = repository.activeIpBan("203.0.113.7", T0);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().target()).contains(alice.uuid());

        assertThat(repository.removeIpBan("203.0.113.7")).isTrue();
        assertThat(repository.activeIpBan("203.0.113.7", T0)).isEmpty();
    }

    @Test
    void seenRecordKeepsTheLastAddressPerAccount() {
        repository.recordSeen(alice, Optional.of("198.51.100.5"), T0);
        repository.recordSeen(bob, Optional.of("198.51.100.5"), T0.plusSeconds(10));

        assertThat(repository.seen(alice)).isPresent();
        assertThat(repository.seen(alice).orElseThrow().lastIp()).contains("198.51.100.5");
        assertThat(repository.seen(bob).orElseThrow().lastIp()).contains("198.51.100.5");
    }

    @Test
    void lockdownFlagDefaultsOffAndRoundTripsBothWays() {
        // The V33 seed row makes a fresh database read not-locked without an upsert.
        assertThat(repository.isLockedDown()).isFalse();

        repository.setLockedDown(true);
        assertThat(repository.isLockedDown()).isTrue();

        repository.setLockedDown(false);
        assertThat(repository.isLockedDown()).isFalse();
    }

    @Test
    void ensureUserExistsMaterializesAnOfflineRowOnce() {
        repository.ensureUserExists(alice, T0);
        repository.ensureUserExists(alice, T0.plusSeconds(99));

        // The first-seen instant is preserved across a second ensure (no overwrite).
        assertThat(repository.seen(alice).orElseThrow().firstSeen()).isEqualTo(T0);
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
