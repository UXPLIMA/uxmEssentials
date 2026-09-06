package com.uxplima.uxmessentials.teleport.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.SearchBudget;

/**
 * A budget-bounded, tick-sliced wrapper around {@link AsyncSafeLocationFinder} that turns "keep sampling until
 * a safe column turns up" into a search that always terminates and never blocks a thread. Each attempt runs one
 * {@link AsyncSafeLocationFinder#find} (sample a point, probe its chunk off-thread, judge it); a miss reschedules
 * the next attempt a few ticks later through the injected {@link Scheduler} rather than looping in place, so a
 * long search is sliced across ticks and never fires every candidate at once or monopolises an async worker. The
 * chain stops the instant the {@link SearchBudget} runs out of any of its ceilings. Attempts, chunk loads, or
 * wall-clock time.
 *
 * <p>This replaces the old refill loop's blocking {@code finder.find(area).get(1500ms)} per candidate: nothing
 * here calls {@code get()}/{@code join()}. The public {@link #search} returns a fresh {@link CompletableFuture}
 * that the chain completes (with the accepted location on a hit, or {@link Optional#empty()} on exhaustion)
 * so a caller composes onto it instead of parking a worker for the whole search.
 *
 * <p>Termination and slicing are the point: {@code maxAttempts} bounds probes, {@code maxChunkLoads} bounds the
 * loads a search may trigger (counted one per attempt), and {@code maxWallClockMillis} bounds elapsed time
 * measured off the injected {@link Clock}; the {@code retryInterval} spaces attempts so the load rate stays gentle.
 */
public final class BudgetedSafeSearch {

    private final AsyncSafeLocationFinder finder;
    private final Scheduler scheduler;
    private final Clock clock;
    private final Duration retryInterval;

    public BudgetedSafeSearch(
            AsyncSafeLocationFinder finder, Scheduler scheduler, Clock clock, Duration retryInterval) {
        this.finder = Objects.requireNonNull(finder, "finder");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retryInterval = Objects.requireNonNull(retryInterval, "retryInterval");
    }

    /**
     * Run a budget-bounded search over {@code area}, returning a future the chain completes with the accepted
     * location or, once {@code budget} is exhausted, {@link Optional#empty()}. Never blocks; retries are
     * tick-sliced through the {@link Scheduler}.
     */
    public CompletableFuture<Optional<RtpSafeLocation>> search(SafeSearchArea area, SearchBudget budget) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(budget, "budget");
        SearchState state = new SearchState(budget, clock.millis());
        CompletableFuture<Optional<RtpSafeLocation>> result = new CompletableFuture<>();
        probeOnce(area, state, result);
        return result;
    }

    private void probeOnce(
            SafeSearchArea area, SearchState state, CompletableFuture<Optional<RtpSafeLocation>> result) {
        state.recordAttempt();
        // The finder never completes exceptionally (the ChunkAccess port always completes, and the pure policy
        // never throws), so thenAccept always runs with the non-null outcome, a present location on a hit, an
        // empty one on a miss, and the result future is never orphaned. The budget bounds the miss chain.
        var ignored = finder.find(area).thenAccept(found -> onProbed(area, state, result, found));
    }

    private void onProbed(
            SafeSearchArea area,
            SearchState state,
            CompletableFuture<Optional<RtpSafeLocation>> result,
            Optional<RtpSafeLocation> found) {
        if (found.isPresent()) {
            result.complete(found);
        } else if (state.mayContinue(clock.millis())) {
            scheduler.asyncAfter(retryInterval, () -> probeOnce(area, state, result));
        } else {
            result.complete(Optional.empty());
        }
    }

    /**
     * The running counter of one search. Ownership: <b>single-search-confined</b>. It is created per
     * {@link #search} call and read/written only inside that call's future chain, where each {@link #probeOnce}
     * strictly happens-after the previous one (through the finder's future and the scheduler hop), never
     * concurrently. The counter is an {@link AtomicInteger} because the chain hands off between the
     * probe-completion thread and the retry thread; that pins visibility across the handoff.
     */
    private static final class SearchState {

        private final SearchBudget budget;
        private final long startMillis;
        private final AtomicInteger attempts = new AtomicInteger();

        SearchState(SearchBudget budget, long startMillis) {
            this.budget = budget;
            this.startMillis = startMillis;
        }

        void recordAttempt() {
            attempts.incrementAndGet();
        }

        boolean mayContinue(long nowMillis) {
            // One chunk load is counted per attempt (a conservative upper bound), so the load count equals the
            // attempt count at every check; the budget still weighs the two ceilings independently.
            int taken = attempts.get();
            return budget.allowsAnotherAttempt(taken, taken, nowMillis - startMillis);
        }
    }
}
