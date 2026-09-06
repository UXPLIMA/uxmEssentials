package com.uxplima.uxmessentials.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the pending-request book: a submitted request is acceptable until its ttl elapses, {@code resolve} matches by
 * requester name or falls back to the newest, a resolved request is removed (so accept and deny each consume it once),
 * and an expired request resolves to {@link TradeRequests.Status#EXPIRED} rather than opening a trade.
 */
class TradeRequestsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final PlayerRef CAROL = new PlayerRef(UUID.randomUUID(), "Carol");

    @Test
    void aSubmittedRequestIsPendingAndResolvesAsMatched() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        TradeRequests requests = new TradeRequests(clock, Duration.ofSeconds(60));

        requests.submit(ALICE, BOB);

        assertThat(requests.hasPending(ALICE.uuid(), BOB.uuid())).isTrue();
        TradeRequests.Match match = requests.resolve(BOB.uuid(), null);
        assertThat(match.status()).isEqualTo(TradeRequests.Status.MATCHED);
        assertThat(requesterOf(match)).isEqualTo(ALICE);
        // Resolving consumed it: a second lookup finds nothing.
        assertThat(requests.resolve(BOB.uuid(), null).status()).isEqualTo(TradeRequests.Status.NONE);
    }

    @Test
    void resolveByNamePicksTheMatchingRequesterAmongSeveral() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        TradeRequests requests = new TradeRequests(clock, Duration.ofSeconds(60));
        requests.submit(ALICE, BOB);
        requests.submit(CAROL, BOB);

        TradeRequests.Match match = requests.resolve(BOB.uuid(), "carol");

        assertThat(match.status()).isEqualTo(TradeRequests.Status.MATCHED);
        assertThat(requesterOf(match)).isEqualTo(CAROL);
        // Alice's request is untouched.
        assertThat(requests.hasPending(ALICE.uuid(), BOB.uuid())).isTrue();
        assertThat(requests.pendingRequesterNames(BOB.uuid())).containsExactly("Alice");
    }

    @Test
    void resolveWithoutANameFallsBackToTheNewestRequest() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        TradeRequests requests = new TradeRequests(clock, Duration.ofSeconds(60));
        requests.submit(ALICE, BOB);
        clock.advance(Duration.ofSeconds(1));
        requests.submit(CAROL, BOB);

        assertThat(requesterOf(requests.resolve(BOB.uuid(), null))).isEqualTo(CAROL);
    }

    @Test
    void anExpiredRequestResolvesAsExpiredAndIsCleared() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        TradeRequests requests = new TradeRequests(clock, Duration.ofSeconds(60));
        requests.submit(ALICE, BOB);

        clock.advance(Duration.ofSeconds(61));

        assertThat(requests.hasPending(ALICE.uuid(), BOB.uuid())).isFalse();
        TradeRequests.Match match = requests.resolve(BOB.uuid(), null);
        assertThat(match.status()).isEqualTo(TradeRequests.Status.EXPIRED);
        assertThat(requesterOf(match)).isEqualTo(ALICE);
        assertThat(requests.resolve(BOB.uuid(), null).status()).isEqualTo(TradeRequests.Status.NONE);
    }

    @Test
    void forgetDropsEveryRequestTouchingAPlayer() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        TradeRequests requests = new TradeRequests(clock, Duration.ofSeconds(60));
        requests.submit(ALICE, BOB);
        requests.submit(BOB, CAROL);

        requests.forget(BOB.uuid());

        assertThat(requests.resolve(BOB.uuid(), null).status()).isEqualTo(TradeRequests.Status.NONE);
        assertThat(requests.resolve(CAROL.uuid(), null).status()).isEqualTo(TradeRequests.Status.NONE);
    }

    private static PlayerRef requesterOf(TradeRequests.Match match) {
        return Objects.requireNonNull(match.request(), "request").requester();
    }

    /** A hand-advanced {@link Clock} so a test can step time deterministically across the ttl boundary. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration delta) {
            now = now.plus(delta);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
