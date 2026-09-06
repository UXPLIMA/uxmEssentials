package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.ChunkAccess;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.BlockTypeName;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;
import com.uxplima.uxmessentials.teleport.domain.SearchBudget;
import com.uxplima.uxmessentials.teleport.domain.YBand;
import org.junit.jupiter.api.Test;

/**
 * Pins the budget-bounded, tick-sliced async safe-search. The search chains attempts through the {@link
 * Scheduler} port (a probe, and on a miss a rescheduled retry a few ticks later) never blocking a thread on
 * a {@code .get()}/{@code .join()}, and it terminates the moment any one of the budget's ceilings (attempts,
 * chunk loads, wall clock) is hit.
 *
 * <p>The fakes make every axis observable: a {@link CountingChunkAccess} decides when a safe column appears
 * and counts probes, a {@link RecordingScheduler} runs each {@code asyncAfter} inline (so the whole chain
 * settles synchronously, which is exactly how the test proves nothing blocked) while recording the delayed
 * reschedules, and a {@link MutableClock} that the scheduler advances by the retry delay lets the wall-clock
 * ceiling be driven deterministically.
 */
class BudgetedSafeSearchTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final SafeSearchArea AREA = new SafeSearchArea(WORLD, 0.0, 0.0, 0.0, 10_000.0, 1_000_000.0);
    private static final SafeSearchPolicy POLICY = new SafeSearchPolicy(
            Set.of(BiomeName.of("ocean")), Set.of(BlockTypeName.of("lava")), YBand.unbounded(), false);
    private static final SafeCandidate SAFE = new SafeCandidate(
            Position.of(WORLD, 500.5, 71.0, 500.5),
            BiomeName.of("plains"),
            true,
            false,
            BlockTypeName.of("grass_block"));
    private static final Instant START = Instant.parse("2026-07-02T00:00:00Z");
    private static final Duration RETRY = Duration.ofMillis(100);

    @Test
    void allUnsafeProbesGiveUpAfterExactlyMaxAttempts() {
        MutableClock clock = new MutableClock(START);
        RecordingScheduler scheduler = new RecordingScheduler(clock);
        CountingChunkAccess access = new CountingChunkAccess(0);
        BudgetedSafeSearch search = searchOver(access, clock, scheduler);

        CompletableFuture<Optional<RtpSafeLocation>> result = search.search(AREA, new SearchBudget(5, 100, 10_000));

        // The chain settled synchronously through the scheduler: no thread was parked on a get()/join().
        assertThat(result).isCompleted();
        assertThat(result.getNow(Optional.empty())).isEmpty();
        assertThat(access.calls()).isEqualTo(5);
    }

    @Test
    void aSafeProbeCompletesWithTheLocationInBoundedAttempts() {
        MutableClock clock = new MutableClock(START);
        RecordingScheduler scheduler = new RecordingScheduler(clock);
        CountingChunkAccess access = new CountingChunkAccess(3);
        BudgetedSafeSearch search = searchOver(access, clock, scheduler);

        CompletableFuture<Optional<RtpSafeLocation>> result = search.search(AREA, new SearchBudget(10, 100, 10_000));

        Optional<RtpSafeLocation> found = result.getNow(Optional.empty());
        assertThat(result).isCompleted();
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().position()).isEqualTo(SAFE.position());
        assertThat(access.calls()).isEqualTo(3);
    }

    @Test
    void eachRetryIsSlicedThroughAsyncAfter() {
        MutableClock clock = new MutableClock(START);
        RecordingScheduler scheduler = new RecordingScheduler(clock);
        CountingChunkAccess access = new CountingChunkAccess(0);
        BudgetedSafeSearch search = searchOver(access, clock, scheduler);

        var ignored = search.search(AREA, new SearchBudget(4, 100, 10_000));

        // Four probes, and a rescheduled retry after each miss except the last, which exhausts the budget.
        assertThat(access.calls()).isEqualTo(4);
        assertThat(scheduler.delays()).hasSize(3).allMatch(RETRY::equals);
        assertThat(scheduler.inlineAsyncCount()).isZero();
    }

    @Test
    void theChunkLoadCeilingTerminatesIndependentlyOfAttempts() {
        MutableClock clock = new MutableClock(START);
        RecordingScheduler scheduler = new RecordingScheduler(clock);
        CountingChunkAccess access = new CountingChunkAccess(0);
        BudgetedSafeSearch search = searchOver(access, clock, scheduler);

        // Loads cap 3, attempts cap 40: the search must stop on the loads cap, far short of the attempts cap.
        CompletableFuture<Optional<RtpSafeLocation>> result = search.search(AREA, new SearchBudget(40, 3, 10_000));

        assertThat(result).isCompleted();
        assertThat(result.getNow(Optional.empty())).isEmpty();
        assertThat(access.calls()).isEqualTo(3);
    }

    @Test
    void theWallClockCeilingTerminatesIndependentlyOfAttempts() {
        MutableClock clock = new MutableClock(START);
        RecordingScheduler scheduler = new RecordingScheduler(clock);
        CountingChunkAccess access = new CountingChunkAccess(0);
        BudgetedSafeSearch search = searchOver(access, clock, scheduler);

        // The scheduler advances the clock by the 100ms retry each hop, so a 250ms deadline bites after a few
        // attempts: well before the 40-attempt ceiling.
        CompletableFuture<Optional<RtpSafeLocation>> result = search.search(AREA, new SearchBudget(40, 100, 250));

        assertThat(result).isCompleted();
        assertThat(result.getNow(Optional.empty())).isEmpty();
        assertThat(access.calls()).isEqualTo(4).isLessThan(40);
    }

    private static BudgetedSafeSearch searchOver(ChunkAccess access, MutableClock clock, RecordingScheduler scheduler) {
        AsyncSafeLocationFinder finder = new AsyncSafeLocationFinder(access, POLICY, clock);
        return new BudgetedSafeSearch(finder, scheduler, clock, RETRY);
    }

    /** A {@link ChunkAccess} that reports a safe column on the configured 1-based probe and counts probes. */
    private static final class CountingChunkAccess implements ChunkAccess {
        private final int safeOnCall;
        private int calls;

        CountingChunkAccess(int safeOnCall) {
            this.safeOnCall = safeOnCall;
        }

        int calls() {
            return calls;
        }

        @Override
        public CompletableFuture<Optional<SafeCandidate>> probe(SafeSearchArea area, int blockX, int blockZ) {
            calls++;
            return CompletableFuture.completedFuture(calls == safeOnCall ? Optional.of(SAFE) : Optional.empty());
        }
    }

    /** A {@link Scheduler} that runs work inline, recording the delayed reschedules and advancing the clock. */
    private static final class RecordingScheduler implements Scheduler {
        private final MutableClock clock;
        private final List<Duration> delays = new ArrayList<>();
        private int inlineAsync;

        RecordingScheduler(MutableClock clock) {
            this.clock = clock;
        }

        List<Duration> delays() {
            return delays;
        }

        int inlineAsyncCount() {
            return inlineAsync;
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            inlineAsync++;
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delays.add(delay);
            clock.advance(delay);
            task.run();
        }
    }

    /** A hand-advanced {@link Clock} so the wall-clock ceiling is driven deterministically by the test. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public long millis() {
            return now.toEpochMilli();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
