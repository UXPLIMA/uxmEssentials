package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The online-only jail countdown through {@link JailCountdown}: a join re-applies a still-active jail
 * (offline-jail enforcement), a tick burns online time off the remainder, and the player is released only
 * when the accrued online time has served the sentence in full, wall-clock time off the server never
 * advances it.
 */
class JailCountdownTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    @Test
    void joinReappliesAStillActiveJail() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveJail(
                TARGET,
                JailState.onlineTimed("cells", Duration.ofMinutes(10), Issuer.console("op"), Optional.empty(), T0));
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        JailCountdown countdown = countdown(repository, sanctions, T0);

        assertThat(countdown.onJoin(TARGET)).isTrue();
        assertThat(sanctions.jailedInto).containsExactly("cells");
    }

    @Test
    void onlineTimeAccruesAcrossSessionsAndReleasesOnlyWhenServed() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveJail(
                TARGET,
                JailState.onlineTimed("cells", Duration.ofMinutes(10), Issuer.console("op"), Optional.empty(), T0));
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        JailCountdown countdown = countdown(repository, sanctions, T0);

        // First session: 4 online minutes elapse: 6 remain, still jailed.
        countdown.onTick(TARGET, Duration.ofMinutes(4));
        assertThat(((JailState.Active) repository.loadJail(TARGET)).remaining()).contains(Duration.ofMinutes(6));
        assertThat(sanctions.released).isEmpty();

        // Second session: 6 more online minutes elapse: served, released.
        countdown.onTick(TARGET, Duration.ofMinutes(6));
        assertThat(repository.loadJail(TARGET)).isInstanceOf(JailState.None.class);
        assertThat(sanctions.released).containsExactly(TARGET);
    }

    @Test
    void aPermanentJailNeverTicksDown() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveJail(TARGET, JailState.permanent("cells", Issuer.console("op"), Optional.empty(), T0));
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        JailCountdown countdown = countdown(repository, sanctions, T0);

        countdown.onTick(TARGET, Duration.ofDays(30));

        assertThat(repository.loadJail(TARGET)).isInstanceOf(JailState.Active.class);
        assertThat(sanctions.released).isEmpty();
    }

    private static JailCountdown countdown(FakeModerationRepository repository, FakeSanctions sanctions, Instant now) {
        return new JailCountdown(
                repository,
                sanctions,
                new RecordingModerationAudit(),
                new ModerationFakes.RecordingEvents(),
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
