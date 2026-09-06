package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.RecordingBroadcast;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.RecordingSink;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.application.port.FakeIpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code /lockdown} toggle and the login gate it drives. The use case must flip the durable flag,
 * confirm to the actor, announce to the broadcast audience and emit one audit line; {@link LoginEnforcement}
 * must refuse a non-bypass login while locked down (the broadest gate, ahead of the ban checks), let a bypass
 * holder through, and stay out of the way when the server is not locked.
 */
class LockdownTest {

    private static final Instant T0 = Instant.parse("2026-06-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);

    private final FakeModerationRepository repository = new FakeModerationRepository();
    private final PlayerRef actor = new PlayerRef(UUID.randomUUID(), "Admin");
    private final PlayerRef joiner = new PlayerRef(UUID.randomUUID(), "Joiner");

    @Test
    void enablingLockdownFlipsTheFlagAndAnnouncesIt() {
        RecordingSink sink = new RecordingSink();
        RecordingBroadcast broadcast = ModerationFakes.broadcast();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        Lockdown lockdown = new Lockdown(repository, ModerationFakes.recordingNotifier(sink), broadcast, audit);

        lockdown.setLockdown(actor, true);

        assertThat(repository.isLockedDown()).isTrue();
        // The actor sees the personal confirmation, not the public line, so a broadcast-receiving actor is
        // not told twice. The broadcast carries the public line for the audience.
        assertThat(sink.sent(actor, ModerationMessageKey.MOD_LOCKDOWN_ENABLED_SELF))
                .isTrue();
        assertThat(sink.sent(actor, ModerationMessageKey.MOD_LOCKDOWN_ENABLED)).isFalse();
        assertThat(broadcast.announced).hasSize(1);
        assertThat(broadcast.announced.get(0).key()).isEqualTo(ModerationMessageKey.MOD_LOCKDOWN_ENABLED);
        assertThat(audit.lines).hasSize(1);
        assertThat(audit.lines.get(0).event()).isEqualTo("server_lockdown");
    }

    @Test
    void disablingLockdownClearsTheFlagAndConfirms() {
        RecordingSink sink = new RecordingSink();
        Lockdown lockdown = new Lockdown(
                repository,
                ModerationFakes.recordingNotifier(sink),
                ModerationFakes.broadcast(),
                new RecordingModerationAudit());
        repository.setLockedDown(true);

        lockdown.setLockdown(actor, false);

        assertThat(repository.isLockedDown()).isFalse();
        assertThat(sink.sent(actor, ModerationMessageKey.MOD_LOCKDOWN_DISABLED_SELF))
                .isTrue();
        assertThat(sink.sent(actor, ModerationMessageKey.MOD_LOCKDOWN_DISABLED)).isFalse();
    }

    @Test
    void lockedDownLoginWithoutBypassIsDeniedWithTheKickMessage() {
        repository.setLockedDown(true);
        LoginEnforcement enforcement = enforcement();

        LoginEnforcement.Decision decision = enforcement.evaluate(joiner, "203.0.113.7", false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.kickReason()).contains(ModerationMessageKey.MOD_LOCKDOWN_KICK.key());
    }

    @Test
    void lockedDownLoginWithBypassIsAllowed() {
        repository.setLockedDown(true);
        LoginEnforcement enforcement = enforcement();

        assertThat(enforcement.evaluate(joiner, "203.0.113.7", true).allowed()).isTrue();
    }

    @Test
    void unlockedLoginIsAllowedRegardlessOfBypass() {
        LoginEnforcement enforcement = enforcement();

        assertThat(enforcement.evaluate(joiner, "203.0.113.7", false).allowed()).isTrue();
    }

    private LoginEnforcement enforcement() {
        return new LoginEnforcement(
                repository,
                ModerationFakes.ipAlts(new FakeIpHistoryStore()),
                ModerationFakes.notifier(),
                new RecordingModerationAudit(),
                CLOCK);
    }
}
