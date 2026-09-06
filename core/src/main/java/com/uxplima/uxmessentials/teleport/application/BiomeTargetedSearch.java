package com.uxplima.uxmessentials.teleport.application;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.SearchBudget;

/**
 * Locates a landing in a specific biome for {@code /rtp biome <biome>}, cheapest source first. It first re-probes
 * the persisted per-biome {@link BiomePoolSlice pool slice}. Columns already known to have validated in the biome,
 * so one async chunk read usually confirms a hit, and only if none still passes does it fall back to a live
 * biome-targeted {@link BudgetedSafeSearch}, whose sampling is hotspot-biased so a rare biome converges within the
 * P1 budget instead of random-hammering the radius. The re-probes are tick-sliced through the {@link Scheduler}
 * exactly like the startup pre-warm, so the slice pass never fires every chunk load at once.
 *
 * <p>Nothing here blocks. {@link #locate} returns a future the async chain completes with the found location or, once
 * both the slice and the budgeted search are exhausted, {@link Optional#empty()}. The slice load: a synchronous
 * relational read: is dispatched off the tick thread through the scheduler before anything else runs.
 */
public final class BiomeTargetedSearch {

    private final BiomePoolSlice slice;
    private final AsyncSafeLocationFinder finder;
    private final BudgetedSafeSearch search;
    private final Supplier<SearchBudget> budget;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration reprobeInterval;
    private final int sliceLimit;

    public BiomeTargetedSearch(
            BiomePoolSlice slice,
            AsyncSafeLocationFinder finder,
            BudgetedSafeSearch search,
            Supplier<SearchBudget> budget,
            Scheduler scheduler,
            Logger log,
            Duration reprobeInterval,
            int sliceLimit) {
        this.slice = Objects.requireNonNull(slice, "slice");
        this.finder = Objects.requireNonNull(finder, "finder");
        this.search = Objects.requireNonNull(search, "search");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.reprobeInterval = Objects.requireNonNull(reprobeInterval, "reprobeInterval");
        if (sliceLimit < 0) {
            throw new IllegalArgumentException("sliceLimit must be >= 0: " + sliceLimit);
        }
        this.sliceLimit = sliceLimit;
    }

    /**
     * Locate a landing in {@code biome} within {@code targetedArea} (which must already carry the target biome), the
     * per-biome pool slice first and a live targeted search second. Returns immediately; the slice load and probes run
     * off-tick.
     */
    public CompletableFuture<Optional<RtpSafeLocation>> locate(SafeSearchArea targetedArea, BiomeName biome) {
        Objects.requireNonNull(targetedArea, "targetedArea");
        Objects.requireNonNull(biome, "biome");
        CompletableFuture<Optional<RtpSafeLocation>> result = new CompletableFuture<>();
        scheduler.async(() -> loadSlice(targetedArea, biome, result));
        return result;
    }

    private void loadSlice(SafeSearchArea area, BiomeName biome, CompletableFuture<Optional<RtpSafeLocation>> result) {
        List<RtpColumn> columns;
        try {
            columns = slice.load(area.world(), biome, sliceLimit);
        } catch (RuntimeException failure) {
            log.warn(
                    "rtp biome slice load failed for world {}: {}",
                    area.world().name(),
                    String.valueOf(failure.getMessage()));
            columns = List.of();
        }
        reprobeNext(area, new ArrayDeque<>(columns), result);
    }

    private void reprobeNext(
            SafeSearchArea area, Deque<RtpColumn> pending, CompletableFuture<Optional<RtpSafeLocation>> result) {
        RtpColumn column = pending.poll();
        if (column == null) {
            runTargetedSearch(area, result);
            return;
        }
        // probeColumn re-runs the full policy (including the target-biome gate) off an async chunk read, so a slice
        // column the world has changed under, or that is no longer that biome, is dropped rather than served stale.
        var ignored = finder.probeColumn(area, column.x(), column.z())
                .thenAccept(found -> onReprobed(area, pending, result, found));
    }

    private void onReprobed(
            SafeSearchArea area,
            Deque<RtpColumn> pending,
            CompletableFuture<Optional<RtpSafeLocation>> result,
            Optional<RtpSafeLocation> found) {
        if (found.isPresent()) {
            result.complete(found);
        } else if (pending.isEmpty()) {
            runTargetedSearch(area, result);
        } else {
            scheduler.asyncAfter(reprobeInterval, () -> reprobeNext(area, pending, result));
        }
    }

    private void runTargetedSearch(SafeSearchArea area, CompletableFuture<Optional<RtpSafeLocation>> result) {
        var ignored = search.search(area, budget.get()).thenAccept(result::complete);
    }
}
