package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * {@code /unwarn} wipes a player's whole warning history in one shot, the natural complement of the
 * append-only {@code /warn}. Clearing a target with warnings removes every row and audit-logs an ok line with
 * the removed count; clearing a target with none is a no-op that still answers the actor and audit-logs the
 * miss, never throwing.
 */
class ClearWarnsTest {

    private static final Instant NOW = Instant.parse("2026-06-02T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    @Test
    void clearRemovesEveryWarningAndAuditsTheCount() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.appendWarn(TARGET, Warn.standing(Issuer.of(ACTOR), Optional.of("first"), NOW));
        repository.appendWarn(TARGET, Warn.standing(Issuer.of(ACTOR), Optional.of("second"), NOW));
        RecordingModerationAudit audit = new RecordingModerationAudit();
        ClearWarns clearWarns = new ClearWarns(repository, ModerationFakes.notifier(), audit);

        clearWarns.clear(ACTOR, TARGET);

        assertThat(repository.warns(TARGET, NOW)).isEmpty();
        assertThat(audit.lines)
                .singleElement()
                .isEqualTo(new RecordingModerationAudit.ClearLine("player_unwarn", true, 2));
    }

    @Test
    void clearOfATargetWithNoWarningsIsANoOp() {
        FakeModerationRepository repository = new FakeModerationRepository();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        ClearWarns clearWarns = new ClearWarns(repository, ModerationFakes.notifier(), audit);

        clearWarns.clear(ACTOR, TARGET);

        assertThat(repository.warns(TARGET, NOW)).isEmpty();
        assertThat(audit.lines)
                .singleElement()
                .isEqualTo(new RecordingModerationAudit.ClearLine("player_unwarn", false, 0));
    }
}
