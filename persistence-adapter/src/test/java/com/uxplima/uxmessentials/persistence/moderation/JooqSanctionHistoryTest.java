package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqSanctionHistory} against the default embedded SQLite backend with the
 * Flyway V11 history table applied. It proves a row round-trips its shape (action, issuer, reason, expiry,
 * ip), that {@code banHistory} returns only the ban-family rows and {@code muteHistory} only the mute-family
 * rows, both newest-first and capped, and that the table is append-only (a lift row coexists with the ban it
 * ended rather than overwriting it). It also proves the unified {@code recentForTarget} folds every kind
 * (ban/mute/warn/kick) newest-first within the limit, {@code recentByActor} scopes to one staff member's
 * actions (others excluded), and a WARN (with its lapse expiry) and a KICK (no expiry) round-trip.
 */
class JooqSanctionHistoryTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final Issuer STAFF = Issuer.console("admin");

    private Persistence persistence;
    private JooqSanctionHistory history;
    private UUID alice;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        history = new JooqSanctionHistory(persistence.dsl());
        alice = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void banRowRoundTripsItsShape() {
        Instant until = T0.plusSeconds(3600);
        history.append(new SanctionHistoryEntry(
                SanctionAction.BAN, alice, STAFF, Optional.of("griefing"), T0, Optional.of(until), Optional.empty()));

        List<SanctionHistoryEntry> rows = history.banHistory(alice, 20);
        assertThat(rows).hasSize(1);
        SanctionHistoryEntry row = rows.get(0);
        assertThat(row.action()).isEqualTo(SanctionAction.BAN);
        assertThat(row.reason()).contains("griefing");
        assertThat(row.expiry()).contains(until);
        assertThat(row.at()).isEqualTo(T0);
        assertThat(row.actor().name()).isEqualTo("admin");
    }

    @Test
    void ipBanRowRoundTripsItsAddress() {
        history.append(new SanctionHistoryEntry(
                SanctionAction.BAN,
                new UUID(0L, 0L),
                STAFF,
                Optional.empty(),
                T0,
                Optional.empty(),
                Optional.of("203.0.113.7")));

        SanctionHistoryEntry row = history.banHistory(new UUID(0L, 0L), 20).get(0);
        assertThat(row.ip()).contains("203.0.113.7");
        assertThat(row.expiry()).isEmpty();
    }

    @Test
    void banHistoryReturnsOnlyBanFamilyNewestFirstAndIsAppendOnly() {
        history.append(entry(SanctionAction.BAN, T0, Optional.of("first")));
        history.append(entry(SanctionAction.MUTE, T0.plusSeconds(30), Optional.of("spam")));
        history.append(entry(SanctionAction.UNBAN, T0.plusSeconds(60), Optional.empty()));

        List<SanctionHistoryEntry> bans = history.banHistory(alice, 20);
        // Both the ban and its later lift are present (append-only), newest-first; the mute is excluded.
        assertThat(bans)
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.UNBAN, SanctionAction.BAN);
    }

    @Test
    void muteHistoryReturnsOnlyMuteFamily() {
        history.append(entry(SanctionAction.MUTE, T0, Optional.of("spam")));
        history.append(entry(SanctionAction.UNMUTE, T0.plusSeconds(60), Optional.empty()));
        history.append(entry(SanctionAction.BAN, T0.plusSeconds(30), Optional.of("grief")));

        assertThat(history.muteHistory(alice, 20))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.UNMUTE, SanctionAction.MUTE);
    }

    @Test
    void readIsCappedAtTheLimit() {
        history.append(entry(SanctionAction.BAN, T0, Optional.of("a")));
        history.append(entry(SanctionAction.UNBAN, T0.plusSeconds(10), Optional.empty()));
        history.append(entry(SanctionAction.BAN, T0.plusSeconds(20), Optional.of("b")));

        assertThat(history.banHistory(alice, 2)).hasSize(2);
    }

    @Test
    void recentForTargetFoldsEveryKindNewestFirst() {
        history.append(entry(SanctionAction.BAN, T0, Optional.of("grief")));
        history.append(entry(SanctionAction.MUTE, T0.plusSeconds(30), Optional.of("spam")));
        history.append(entry(SanctionAction.WARN, T0.plusSeconds(60), Optional.of("language")));
        history.append(entry(SanctionAction.KICK, T0.plusSeconds(90), Optional.of("afk")));

        assertThat(history.recentForTarget(alice, 20))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.KICK, SanctionAction.WARN, SanctionAction.MUTE, SanctionAction.BAN);
    }

    @Test
    void recentForTargetIsCappedAtTheLimit() {
        history.append(entry(SanctionAction.BAN, T0, Optional.of("a")));
        history.append(entry(SanctionAction.WARN, T0.plusSeconds(10), Optional.of("b")));
        history.append(entry(SanctionAction.KICK, T0.plusSeconds(20), Optional.of("c")));

        assertThat(history.recentForTarget(alice, 2))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.KICK, SanctionAction.WARN);
    }

    @Test
    void recentByActorFiltersByActorNewestFirstAndIsCapped() {
        Issuer first = staff("Mod1");
        Issuer second = staff("Mod2");
        UUID bob = UUID.randomUUID();
        // Two actions by `first` against different targets, one by `second` (must be excluded).
        history.append(byActor(SanctionAction.WARN, first, alice, T0, Optional.of("warn-a")));
        history.append(byActor(SanctionAction.KICK, second, alice, T0.plusSeconds(15), Optional.of("kick-by-other")));
        history.append(byActor(SanctionAction.BAN, first, bob, T0.plusSeconds(30), Optional.of("ban-b")));

        List<SanctionHistoryEntry> byFirst = history.recentByActor(first.uuid().orElseThrow(), 20);
        assertThat(byFirst)
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.BAN, SanctionAction.WARN);
        assertThat(byFirst).allSatisfy(row -> assertThat(row.actor().name()).isEqualTo("Mod1"));

        assertThat(history.recentByActor(first.uuid().orElseThrow(), 1))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.BAN);
    }

    @Test
    void allSinceReturnsEveryActorsRowsFromThresholdNewestFirstAndCapped() {
        Issuer modOne = staff("Mod1");
        Issuer modTwo = staff("Mod2");
        UUID bob = UUID.randomUUID();
        // Two in-window rows by different actors against different targets, and one before the threshold.
        history.append(byActor(SanctionAction.WARN, modOne, alice, T0.minusSeconds(600), Optional.of("old")));
        history.append(byActor(SanctionAction.BAN, modOne, alice, T0, Optional.of("ban")));
        history.append(byActor(SanctionAction.MUTE, modTwo, bob, T0.plusSeconds(30), Optional.of("mute")));

        List<SanctionHistoryEntry> since = history.allSince(T0, 20);
        assertThat(since)
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.MUTE, SanctionAction.BAN);

        assertThat(history.allSince(T0, 1))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.MUTE);
    }

    @Test
    void warnAndKickRowsRoundTripThroughTheUnifiedRead() {
        Instant lapse = T0.plusSeconds(86400);
        history.append(new SanctionHistoryEntry(
                SanctionAction.WARN, alice, STAFF, Optional.of("language"), T0, Optional.of(lapse), Optional.empty()));
        history.append(new SanctionHistoryEntry(
                SanctionAction.KICK,
                alice,
                STAFF,
                Optional.of("afk"),
                T0.plusSeconds(10),
                Optional.empty(),
                Optional.empty()));

        List<SanctionHistoryEntry> rows = history.recentForTarget(alice, 20);
        SanctionHistoryEntry kick = rows.get(0);
        assertThat(kick.action()).isEqualTo(SanctionAction.KICK);
        assertThat(kick.reason()).contains("afk");
        assertThat(kick.expiry()).isEmpty();

        SanctionHistoryEntry warn = rows.get(1);
        assertThat(warn.action()).isEqualTo(SanctionAction.WARN);
        assertThat(warn.reason()).contains("language");
        assertThat(warn.expiry()).contains(lapse);
    }

    private SanctionHistoryEntry entry(SanctionAction action, Instant at, Optional<String> reason) {
        return new SanctionHistoryEntry(action, alice, STAFF, reason, at, Optional.empty(), Optional.empty());
    }

    private SanctionHistoryEntry byActor(
            SanctionAction action, Issuer actor, UUID target, Instant at, Optional<String> reason) {
        return new SanctionHistoryEntry(action, target, actor, reason, at, Optional.empty(), Optional.empty());
    }

    private static Issuer staff(String name) {
        return Issuer.stored(Optional.of(UUID.randomUUID()), name);
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
