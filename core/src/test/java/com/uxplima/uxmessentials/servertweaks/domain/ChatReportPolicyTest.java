package com.uxplima.uxmessentials.servertweaks.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the no-chat-reports decision: a disabled tweak never re-delivers anything, and an enabled tweak re-delivers
 * only the messages that actually arrived signed: an already-unsigned message is left alone even when the tweak is on.
 */
class ChatReportPolicyTest {

    @Test
    void disabledNeverRequestsUnsignedDelivery() {
        ChatReportPolicy policy = new ChatReportPolicy(false);

        assertThat(policy.enabled()).isFalse();
        assertThat(policy.shouldDeliverUnsigned(false)).isFalse();
        assertThat(policy.shouldDeliverUnsigned(true)).isFalse();
    }

    @Test
    void enabledRedeliversOnlySignedMessages() {
        ChatReportPolicy policy = new ChatReportPolicy(true);

        assertThat(policy.enabled()).isTrue();
        // A signed message (alreadyUnsigned = false) is the one worth re-delivering unsigned.
        assertThat(policy.shouldDeliverUnsigned(false)).isTrue();
        // An already-unsigned message has no signature to strip, so it is left to flow normally.
        assertThat(policy.shouldDeliverUnsigned(true)).isFalse();
    }
}
