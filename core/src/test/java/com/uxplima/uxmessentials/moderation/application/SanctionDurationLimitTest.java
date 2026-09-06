package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * {@link SanctionDurationLimit} clamps a requested span to the actor's {@code maxduration} tier. An actor with
 * no node (the exempt fake resolves to the {@code -1} unlimited default) is uncapped; an actor whose tier is a
 * finite number of seconds has any over-cap request. Including a permanent ban modelled as the far-future
 * span: reduced to that ceiling, while an under-cap request passes through unchanged.
 */
class SanctionDurationLimitTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");

    @Test
    void noTierLeavesTheRequestedSpanUncapped() {
        SanctionDurationLimit limit = new SanctionDurationLimit(ModerationFakes.exempt());

        assertThat(limit.cap(ACTOR, "ban", Duration.ofDays(7))).isEqualTo(Duration.ofDays(7));
        assertThat(limit.cap(ACTOR, "ban", Ban.PERMANENT_SPAN)).isEqualTo(Ban.PERMANENT_SPAN);
    }

    @Test
    void anOverCapRequestIsReducedToTheTierCeiling() {
        SanctionDurationLimit limit = new SanctionDurationLimit(ModerationFakes.capping(3600));

        assertThat(limit.cap(ACTOR, "ban", Duration.ofDays(1))).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void anUnderCapRequestPassesThroughUnchanged() {
        SanctionDurationLimit limit = new SanctionDurationLimit(ModerationFakes.capping(3600));

        assertThat(limit.cap(ACTOR, "mute", Duration.ofMinutes(30))).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void aPermanentSpanIsReducedToAFiniteTier() {
        SanctionDurationLimit limit = new SanctionDurationLimit(ModerationFakes.capping(3600));

        assertThat(limit.cap(ACTOR, "ban", Ban.PERMANENT_SPAN)).isEqualTo(Duration.ofHours(1));
    }
}
