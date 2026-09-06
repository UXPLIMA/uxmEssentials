package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.vote.domain.BroadcastType;
import org.junit.jupiter.api.Test;

/**
 * The pure broadcast policy: every {@link BroadcastType} against the facts of a credited vote, plus the
 * blacklist short-circuit and the cooldown-window edges. No port is involved. The decision is a function
 * of its arguments.
 */
class BroadcastDecisionTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_000_000);
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    @Test
    void blacklistedVoterIsNeverBroadcastRegardlessOfType() {
        for (BroadcastType type : BroadcastType.values()) {
            assertThat(BroadcastDecision.shouldBroadcast(type, true, 1, true, Optional.empty(), NOW, COOLDOWN))
                    .as("type %s with blacklist", type)
                    .isFalse();
        }
    }

    @Test
    void noneNeverBroadcasts() {
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.NONE, true, 1, false, Optional.empty(), NOW, COOLDOWN))
                .isFalse();
    }

    @Test
    void everyVoteAlwaysBroadcastsOnlineOrOffline() {
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.EVERY_VOTE, true, 7, false, Optional.empty(), NOW, COOLDOWN))
                .isTrue();
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.EVERY_VOTE, false, 7, false, Optional.empty(), NOW, COOLDOWN))
                .isTrue();
    }

    @Test
    void onlineOnlyBroadcastsOnlyWhenTheVoterIsOnline() {
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.ONLINE_ONLY, true, 3, false, Optional.empty(), NOW, COOLDOWN))
                .isTrue();
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.ONLINE_ONLY, false, 3, false, Optional.empty(), NOW, COOLDOWN))
                .isFalse();
    }

    @Test
    void firstVoteOfDayBroadcastsOnlyWhenTheDailyCountIsExactlyOne() {
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.FIRST_VOTE_OF_DAY, true, 1, false, Optional.empty(), NOW, COOLDOWN))
                .isTrue();
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.FIRST_VOTE_OF_DAY, true, 2, false, Optional.empty(), NOW, COOLDOWN))
                .isFalse();
    }

    @Test
    void firstVoteOfDayIgnoresOnlineState() {
        // The daily count is what matters, not whether the voter happens to be online.
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.FIRST_VOTE_OF_DAY, false, 1, false, Optional.empty(), NOW, COOLDOWN))
                .isTrue();
    }

    @Test
    void cooldownBroadcastsWhenTheVoterHasNeverBeenBroadcast() {
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.COOLDOWN_PER_PLAYER, true, 1, false, Optional.empty(), NOW, COOLDOWN))
                .isTrue();
    }

    @Test
    void cooldownSuppressesWhenTheVoterIsOffline() {
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.COOLDOWN_PER_PLAYER, false, 1, false, Optional.empty(), NOW, COOLDOWN))
                .isFalse();
    }

    @Test
    void cooldownSuppressesStrictlyInsideTheWindow() {
        // last + cooldown is one second in the future → now is before it → suppressed.
        Instant last = NOW.minus(COOLDOWN).plusSeconds(1);
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.COOLDOWN_PER_PLAYER, true, 1, false, Optional.of(last), NOW, COOLDOWN))
                .isFalse();
    }

    @Test
    void cooldownBroadcastsExactlyAtTheWindowBoundary() {
        // last + cooldown == now → not before → broadcasts (boundary is inclusive of broadcasting).
        Instant last = NOW.minus(COOLDOWN);
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.COOLDOWN_PER_PLAYER, true, 1, false, Optional.of(last), NOW, COOLDOWN))
                .isTrue();
    }

    @Test
    void cooldownBroadcastsAfterTheWindowHasElapsed() {
        Instant last = NOW.minus(COOLDOWN).minusSeconds(1);
        assertThat(BroadcastDecision.shouldBroadcast(
                        BroadcastType.COOLDOWN_PER_PLAYER, true, 1, false, Optional.of(last), NOW, COOLDOWN))
                .isTrue();
    }
}
