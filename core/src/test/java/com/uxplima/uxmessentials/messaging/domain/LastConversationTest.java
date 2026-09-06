package com.uxplima.uxmessentials.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The reply-TTL rule the {@link LastConversation} aggregate owns: a {@code /reply} resolves to the last
 * partner only while the conversation is fresh, and a non-positive TTL never expires. The aggregate is pure,
 * so each rule is asserted here in isolation.
 */
class LastConversationTest {

    private static final PlayerRef PARTNER = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final Instant TOUCHED = Instant.parse("2026-05-30T12:00:00Z");

    @Test
    void isFreshWithinTheTtlWindow() {
        LastConversation conversation = LastConversation.with(PARTNER, TOUCHED);

        assertThat(conversation.isFresh(TOUCHED.plusSeconds(60), Duration.ofMinutes(5)))
                .isTrue();
    }

    @Test
    void isStaleOnceTheTtlHasElapsed() {
        LastConversation conversation = LastConversation.with(PARTNER, TOUCHED);

        assertThat(conversation.isFresh(TOUCHED.plusSeconds(301), Duration.ofMinutes(5)))
                .isFalse();
    }

    @Test
    void isFreshExactlyAtTheBoundary() {
        LastConversation conversation = LastConversation.with(PARTNER, TOUCHED);

        // now == lastTouched + ttl is still fresh; only strictly after the window is stale.
        assertThat(conversation.isFresh(TOUCHED.plus(Duration.ofMinutes(5)), Duration.ofMinutes(5)))
                .isTrue();
    }

    @Test
    void aNonPositiveTtlNeverExpires() {
        LastConversation conversation = LastConversation.with(PARTNER, TOUCHED);

        assertThat(conversation.isFresh(TOUCHED.plus(Duration.ofDays(365)), Duration.ZERO))
                .isTrue();
        assertThat(conversation.isFresh(TOUCHED.plus(Duration.ofDays(365)), Duration.ofSeconds(-1)))
                .isTrue();
    }

    @Test
    void touchedAtReStampsTheWindowKeepingThePartner() {
        LastConversation refreshed = LastConversation.with(PARTNER, TOUCHED).touchedAt(TOUCHED.plusSeconds(600));

        assertThat(refreshed.partner()).isEqualTo(PARTNER);
        // The re-stamp moves the freshness window forward: what was stale before is fresh now.
        assertThat(refreshed.isFresh(TOUCHED.plusSeconds(660), Duration.ofMinutes(5)))
                .isTrue();
    }
}
