package com.uxplima.uxmessentials.villagers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure restock-timer rule: a restock is due once the interval has elapsed since the last restock (inclusive),
 * a more recent restock is not due, a villager that never restocked (last restock at the epoch) is always due, and a
 * non-positive interval is rejected at construction.
 */
class RestockPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void restockIsDueOnceTheIntervalHasElapsed() {
        RestockPolicy policy = RestockPolicy.ofSeconds(60);

        // Last restock 61s ago, older than the 60s interval, so a restock is due.
        assertThat(policy.restockDue(NOW.minusSeconds(61), NOW)).isTrue();
    }

    @Test
    void restockIsDueExactlyAtTheInterval() {
        RestockPolicy policy = RestockPolicy.ofSeconds(60);

        // The comparison is inclusive: a restock exactly one interval old is due.
        assertThat(policy.restockDue(NOW.minusSeconds(60), NOW)).isTrue();
    }

    @Test
    void restockIsNotDueBeforeTheInterval() {
        RestockPolicy policy = RestockPolicy.ofSeconds(60);

        // Last restock 30s ago: younger than the interval, so no restock is due yet.
        assertThat(policy.restockDue(NOW.minusSeconds(30), NOW)).isFalse();
    }

    @Test
    void aVillagerThatNeverRestockedIsAlwaysDue() {
        RestockPolicy policy = RestockPolicy.ofSeconds(600);

        // The adapter models "never restocked" as a last restock at the epoch, which is due on the first sweep.
        assertThat(policy.restockDue(Instant.EPOCH, NOW)).isTrue();
    }

    @Test
    void rejectsANonPositiveInterval() {
        assertThatThrownBy(() -> new RestockPolicy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RestockPolicy(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
