package com.uxplima.uxmessentials.presence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The presence aggregate's transition rules in one place: the AFK auto-transition (idle past a threshold), the
 * manual AFK toggle with its reason, the activity-clears-AFK rule, and the vanish flip. Each returning a new
 * immutable aggregate so a concurrent reader (the async sweep, a cross-context vanish lookup) never sees a
 * half-applied change. These are the rules the {@code ConcurrentHashMap} store re-applies under {@code compute}.
 */
class PlayerPresenceTest {

    private static final Instant T0 = Instant.parse("2026-05-30T12:00:00Z");
    private static final Duration IDLE = Duration.ofMinutes(5);

    @Test
    void activeIsTheNeutralState() {
        PlayerPresence presence = PlayerPresence.active(T0);

        assertThat(presence.afk()).isFalse();
        assertThat(presence.afkReason()).isEmpty();
        assertThat(presence.lastActivity()).isEqualTo(T0);
        assertThat(presence.vanished()).isFalse();
    }

    @Test
    void markAfkSetsTheFlagAndReasonWithoutTouchingTheOriginal() {
        PlayerPresence active = PlayerPresence.active(T0);

        PlayerPresence afk = active.markAfk(Optional.of("lunch"));

        assertThat(afk.afk()).isTrue();
        assertThat(afk.afkReason()).contains("lunch");
        assertThat(afk.lastActivity()).isEqualTo(T0); // the activity stamp is preserved, not bumped
        assertThat(active.afk()).isFalse(); // the source aggregate is immutable
    }

    @Test
    void autoAfkCarriesNoReason() {
        PlayerPresence afk = PlayerPresence.active(T0).markAfk(Optional.empty());

        assertThat(afk.afk()).isTrue();
        assertThat(afk.afkReason()).isEmpty();
    }

    @Test
    void clearedReturnsToActiveAndRestampsTheClock() {
        Instant later = T0.plus(Duration.ofMinutes(10));
        PlayerPresence afk = PlayerPresence.active(T0).markAfk(Optional.of("brb"));

        PlayerPresence back = afk.cleared(later);

        assertThat(back.afk()).isFalse();
        assertThat(back.afkReason()).isEmpty();
        assertThat(back.lastActivity()).isEqualTo(later);
    }

    @Test
    void idlePastIsFalseBeforeTheThresholdElapses() {
        PlayerPresence presence = PlayerPresence.active(T0);

        assertThat(presence.isIdlePast(T0.plus(Duration.ofMinutes(4)), IDLE)).isFalse();
    }

    @Test
    void idlePastIsTrueOnceTheThresholdElapses() {
        PlayerPresence presence = PlayerPresence.active(T0);

        assertThat(presence.isIdlePast(T0.plus(Duration.ofMinutes(6)), IDLE)).isTrue();
    }

    @Test
    void anAlreadyAfkPlayerIsNeverReportedIdle() {
        PlayerPresence afk = PlayerPresence.active(T0).markAfk(Optional.empty());

        assertThat(afk.isIdlePast(T0.plus(Duration.ofHours(1)), IDLE)).isFalse();
    }

    @Test
    void aNonPositiveThresholdDisablesAutoAfk() {
        PlayerPresence presence = PlayerPresence.active(T0);

        assertThat(presence.isIdlePast(T0.plus(Duration.ofHours(1)), Duration.ZERO))
                .isFalse();
        assertThat(presence.isIdlePast(T0.plus(Duration.ofHours(1)), Duration.ofSeconds(-5)))
                .isFalse();
    }

    @Test
    void vanishSetsTheFlagAndLeavesAfkUntouched() {
        PlayerPresence afk = PlayerPresence.active(T0).markAfk(Optional.of("away"));

        PlayerPresence vanished = afk.vanish();

        assertThat(vanished.vanished()).isTrue();
        assertThat(vanished.afk()).isTrue(); // vanish is orthogonal to AFK
        assertThat(vanished.afkReason()).contains("away");
    }

    @Test
    void unvanishClearsOnlyTheVanishFlag() {
        PlayerPresence vanished =
                PlayerPresence.active(T0).markAfk(Optional.empty()).vanish();

        PlayerPresence shown = vanished.unvanish();

        assertThat(shown.vanished()).isFalse();
        assertThat(shown.afk()).isTrue();
    }

    @Test
    void activityPreservesTheVanishFlag() {
        PlayerPresence vanishedAfk =
                PlayerPresence.active(T0).markAfk(Optional.empty()).vanish();

        PlayerPresence cleared = vanishedAfk.cleared(T0.plus(Duration.ofMinutes(1)));

        assertThat(cleared.afk()).isFalse(); // activity returns from AFK
        assertThat(cleared.vanished()).isTrue(); // but does not reveal a vanished player
    }
}
