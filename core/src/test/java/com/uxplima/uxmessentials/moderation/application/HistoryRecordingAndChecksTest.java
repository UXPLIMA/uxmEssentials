package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationAction;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.RecordingSink;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.FakeIpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * B-1 behaviours: warns and kicks now land in the append-only history on the success path, and the unified
 * checks ({@code /checkban}, {@code /checkmute}) read the live sanction state. The escalation case is the
 * important one: a warn that trips a rung records the {@code WARN} once and the escalated sanction once, never
 * a doubled warn.
 */
class HistoryRecordingAndChecksTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    private FakeModerationRepository repository;
    private FakeSanctionHistory store;
    private SanctionHistoryRecorder recorder;
    private RecordingModerationAudit audit;
    private ModerationFakes.RecordingEvents events;
    private ModerationFakes.RecordingBroadcast broadcast;
    private Notifier notifier;
    private ModerationGuard guard;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repository = new FakeModerationRepository();
        store = new FakeSanctionHistory();
        audit = new RecordingModerationAudit();
        events = new ModerationFakes.RecordingEvents();
        broadcast = ModerationFakes.broadcast();
        notifier = ModerationFakes.recordingNotifier(new RecordingSink());
        guard = new ModerationGuard(ModerationFakes.exempt());
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        recorder = new SanctionHistoryRecorder(store, clock);
    }

    // --- recording on the success path ------------------------------------------------------------------

    @Test
    void warnRecordsOneWarnRowWithNoExpiry() {
        issueWarn(WarnEscalation.NONE).warn(ACTOR, TARGET, Optional.of("spam"), false);

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.WARN);
        assertThat(row.target()).isEqualTo(TARGET.uuid());
        assertThat(row.expiry()).isEmpty();
    }

    @Test
    void tempWarnRecordsOneWarnRowWithTheLapseExpiry() {
        tempWarn().warn(ACTOR, TARGET, "7d", Optional.of("spam"), false);

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.WARN);
        assertThat(row.expiry()).contains(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void kickRecordsOneKickRow() {
        new Kick(new FakeSanctions(TARGET), guard, notifier, audit, recorder, broadcast)
                .kick(ACTOR, TARGET, Optional.of("afk"), false);

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.KICK);
        assertThat(row.target()).isEqualTo(TARGET.uuid());
    }

    @Test
    void anExemptKickRecordsNothing() {
        ModerationGuard exemptGuard = new ModerationGuard(ModerationFakes.exempt(TARGET.uuid()));
        new Kick(new FakeSanctions(TARGET), exemptGuard, notifier, audit, recorder, broadcast)
                .kick(ACTOR, TARGET, Optional.of("afk"), false);

        assertThat(store.appended).isEmpty();
    }

    // --- the escalation invariant: WARN once + the escalated sanction once -------------------------------

    @Test
    void aWarnThatEscalatesToAKickRecordsTheWarnOnceAndTheKickOnce() {
        WarnEscalation ladder =
                new WarnEscalation(List.of(new EscalationStep(1, EscalationAction.KICK, Optional.empty())));

        issueWarn(ladder).warn(ACTOR, TARGET, Optional.of("final"), false);

        assertThat(store.appended).hasSize(2);
        assertThat(store.appended.stream().filter(e -> e.action() == SanctionAction.WARN))
                .hasSize(1);
        assertThat(store.appended.stream().filter(e -> e.action() == SanctionAction.KICK))
                .hasSize(1);
    }

    @Test
    void aWarnThatEscalatesToATempbanRecordsTheWarnOnceAndTheBanOnce() {
        WarnEscalation ladder = new WarnEscalation(
                List.of(new EscalationStep(1, EscalationAction.TEMPBAN, Optional.of(Duration.ofHours(2)))));

        issueWarn(ladder).warn(ACTOR, TARGET, Optional.empty(), false);

        assertThat(store.appended.stream().filter(e -> e.action() == SanctionAction.WARN))
                .hasSize(1);
        assertThat(store.appended.stream().filter(e -> e.action() == SanctionAction.BAN))
                .hasSize(1);
        assertThat(store.appended.stream().filter(e -> e.action() == SanctionAction.MUTE))
                .isEmpty();
    }

    // --- /checkban, /checkmute --------------------------------------------------------------------------

    @Test
    void checkBanReportsBannedWhenABanIsActive() {
        repository.saveTempban(
                TARGET,
                TempbanState.active(
                        NOW.plus(Duration.ofDays(1)),
                        com.uxplima.uxmessentials.moderation.domain.Issuer.of(ACTOR),
                        Optional.of("grief"),
                        NOW));
        RecordingSink sink = new RecordingSink();

        new CheckBan(repository, ModerationFakes.recordingNotifier(sink), clock).show(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_CHECK_BANNED)).isTrue();
    }

    @Test
    void checkBanReportsBannedForAPermanentBan() {
        repository.saveTempban(
                TARGET,
                TempbanState.active(
                        Instant.MAX,
                        com.uxplima.uxmessentials.moderation.domain.Issuer.of(ACTOR),
                        Optional.of("grief"),
                        NOW));
        RecordingSink sink = new RecordingSink();

        new CheckBan(repository, ModerationFakes.recordingNotifier(sink), clock).show(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_CHECK_BANNED)).isTrue();
    }

    @Test
    void checkBanReportsNotBannedWhenThereIsNoBan() {
        RecordingSink sink = new RecordingSink();

        new CheckBan(repository, ModerationFakes.recordingNotifier(sink), clock).show(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_CHECK_NOT_BANNED)).isTrue();
    }

    @Test
    void checkBanReportsNotBannedWhenTheTimedBanHasLapsed() {
        repository.saveTempban(
                TARGET,
                TempbanState.active(
                        NOW.minus(Duration.ofMinutes(1)),
                        com.uxplima.uxmessentials.moderation.domain.Issuer.of(ACTOR),
                        Optional.of("grief"),
                        NOW.minus(Duration.ofHours(1))));
        RecordingSink sink = new RecordingSink();

        new CheckBan(repository, ModerationFakes.recordingNotifier(sink), clock).show(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_CHECK_NOT_BANNED)).isTrue();
    }

    @Test
    void checkMuteReportsMutedWhenAMuteIsActive() {
        repository.saveMute(
                TARGET,
                MuteState.timed(
                        NOW.plus(Duration.ofHours(1)),
                        com.uxplima.uxmessentials.moderation.domain.Issuer.of(ACTOR),
                        Optional.of("spam"),
                        NOW));
        RecordingSink sink = new RecordingSink();

        new CheckMute(repository, ModerationFakes.recordingNotifier(sink), clock).show(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_CHECK_MUTED)).isTrue();
    }

    @Test
    void checkMuteReportsNotMutedWhenTheTimedMuteHasLapsed() {
        repository.saveMute(
                TARGET,
                MuteState.timed(
                        NOW.minus(Duration.ofMinutes(1)),
                        com.uxplima.uxmessentials.moderation.domain.Issuer.of(ACTOR),
                        Optional.of("spam"),
                        NOW.minus(Duration.ofHours(1))));
        RecordingSink sink = new RecordingSink();

        new CheckMute(repository, ModerationFakes.recordingNotifier(sink), clock).show(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.MOD_CHECK_NOT_MUTED)).isTrue();
    }

    // --- builders ---------------------------------------------------------------------------------------

    private SanctionHistoryEntry single() {
        assertThat(store.appended).hasSize(1);
        return store.appended.get(0);
    }

    private IssueWarn issueWarn(WarnEscalation ladder) {
        return new IssueWarn(repository, guard, notifier, audit, events, recorder, broadcast, escalator(ladder), clock);
    }

    private TempWarn tempWarn() {
        return new TempWarn(
                repository, guard, notifier, audit, events, recorder, broadcast, escalator(WarnEscalation.NONE), clock);
    }

    private WarnEscalator escalator(WarnEscalation ladder) {
        SanctionDurationLimit limit = new SanctionDurationLimit(ModerationFakes.exempt());
        Mute mute = new Mute(
                repository,
                guard,
                notifier,
                audit,
                events,
                recorder,
                limit,
                broadcast,
                com.uxplima.uxmessentials.moderation.application.port.SanctionSync.NONE,
                clock);
        TempBan tempBan = new TempBan(
                repository,
                new FakeSanctions(TARGET),
                guard,
                notifier,
                audit,
                events,
                recorder,
                limit,
                broadcast,
                com.uxplima.uxmessentials.moderation.application.port.SanctionSync.NONE,
                clock);
        Ban ban = new Ban(
                repository,
                new FakeSanctions(TARGET),
                guard,
                notifier,
                audit,
                events,
                recorder,
                limit,
                broadcast,
                com.uxplima.uxmessentials.moderation.application.port.SanctionSync.NONE,
                com.uxplima.uxmessentials.moderation.domain.AddressStrictness.NORMAL,
                new FakeIpHistoryStore(),
                clock);
        Kick kick = new Kick(new FakeSanctions(TARGET), guard, notifier, audit, recorder, broadcast);
        return new WarnEscalator(ladder, mute, tempBan, ban, kick, notifier);
    }
}
