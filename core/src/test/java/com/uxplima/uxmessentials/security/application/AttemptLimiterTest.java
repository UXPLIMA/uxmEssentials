package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the shared brute-force limiter: it accumulates failures per account (not per session), so a budget that reaches
 * {@code maxAttempts - 1} and is then "resumed" (as a reconnect would) locks out on the next failure rather than
 * resetting; a success clears the budget; and an expired lockout starts a fresh one.
 */
class AttemptLimiterTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCKOUT = Duration.ofMinutes(5);

    private final UUID player = UUID.randomUUID();

    private AttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AttemptLimiter(new LockoutPolicy(MAX_ATTEMPTS), LOCKOUT);
    }

    @Test
    void theFailureBudgetSurvivesAReconnectAndLocksOutRatherThanResetting() {
        // Reach maxAttempts - 1 failures (RETRY each), as an attacker would before disconnecting.
        for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
            assertThat(limiter.recordFailure(player, NOW).lockedOut()).isFalse();
        }
        assertThat(limiter.isLockedOut(player, NOW)).isFalse();

        // A reconnect touches no reset on the account-scoped counter (there is nothing here that a rejoin clears), so
        // the very next failure is the maxAttempts-th and locks the account out: the budget was not reset.
        AttemptLimiter.Outcome outcome = limiter.recordFailure(player, NOW);

        assertThat(outcome.lockedOut()).isTrue();
        assertThat(limiter.isLockedOut(player, NOW)).isTrue();
    }

    @Test
    void reportsTheRemainingAttemptsUntilTheLockout() {
        assertThat(limiter.recordFailure(player, NOW).remaining()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(limiter.recordFailure(player, NOW).remaining()).isEqualTo(MAX_ATTEMPTS - 2);
    }

    @Test
    void aSuccessfulProofResetsTheBudget() {
        limiter.recordFailure(player, NOW);
        limiter.recordFailure(player, NOW);

        limiter.recordSuccess(player);

        // Back to a full budget: the next failure is only the first of a fresh run.
        assertThat(limiter.recordFailure(player, NOW).remaining()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(limiter.isLockedOut(player, NOW)).isFalse();
    }

    @Test
    void theLockoutExpiresAndStartsAFreshBudget() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            limiter.recordFailure(player, NOW);
        }
        assertThat(limiter.isLockedOut(player, NOW)).isTrue();

        Instant afterCooldown = NOW.plus(LOCKOUT).plusSeconds(1);
        assertThat(limiter.isLockedOut(player, afterCooldown)).isFalse();
        // The expired lockout also cleared the counter, so the account gets its full budget back.
        assertThat(limiter.recordFailure(player, afterCooldown).remaining()).isEqualTo(MAX_ATTEMPTS - 1);
    }

    @Test
    void clearAllDropsEveryCounterAndLockout() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            limiter.recordFailure(player, NOW);
        }
        assertThat(limiter.isLockedOut(player, NOW)).isTrue();

        limiter.clearAll();

        assertThat(limiter.isLockedOut(player, NOW)).isFalse();
    }

    @Test
    void rejectsANegativeLockout() {
        assertThatThrownBy(() -> new AttemptLimiter(new LockoutPolicy(3), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
