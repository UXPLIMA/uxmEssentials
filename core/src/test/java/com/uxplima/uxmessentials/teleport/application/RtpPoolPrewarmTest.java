package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.ChunkAccess;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.BlockTypeName;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;
import com.uxplima.uxmessentials.teleport.domain.YBand;
import org.junit.jupiter.api.Test;

/**
 * Pins the startup pre-warm: the payoff of the persisted pool. Given a store returning persisted columns and a
 * chunk-access port that re-probes each (some now safe, some no longer), the prewarm re-runs the real {@link
 * SafeSearchPolicy} over every column and offers only the still-valid ones to the queue sink: a column the world
 * changed under is re-validated to empty and dropped, never served blind. It also proves the load is bounded by
 * {@code limit} and that the whole thing is non-blocking (nothing here joins a future. The inline scheduler shows
 * the chain composes rather than awaits).
 */
class RtpPoolPrewarmTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant WHEN = Instant.parse("2026-07-02T00:00:00Z");
    private static final int UNSAFE_X = 600;

    // A wide annulus at the origin so every re-probed column is in bounds; only the ground/biome checks decide.
    private final SafeSearchArea area = new SafeSearchArea(WORLD, 0.0, 0.0, 0.0, 10_000.0, 1_000_000.0);
    private final SafeSearchPolicy policy = new SafeSearchPolicy(
            Set.of(BiomeName.of("ocean")), Set.of(BlockTypeName.of("lava")), YBand.unbounded(), false);
    private final Clock clock = Clock.fixed(WHEN, ZoneOffset.UTC);
    private final RecordingScheduler scheduler = new RecordingScheduler();

    @Test
    void reprobesEveryPersistedColumnAndDropsTheOnesThatNoLongerValidate() {
        FakeStore store = new FakeStore(List.of(
                new RtpColumn(WORLD, 500, 500, WHEN),
                new RtpColumn(WORLD, UNSAFE_X, UNSAFE_X, WHEN),
                new RtpColumn(WORLD, 700, 700, WHEN)));
        List<RtpSafeLocation> warmed = new ArrayList<>();

        prewarm(store).prewarm(area, 10, warmed::add);

        // The middle column re-probes unsafe now and is dropped; only the two still-valid columns re-enter the queue.
        assertThat(warmed).extracting(loc -> loc.position().blockX()).containsExactlyInAnyOrder(500, 700);
        assertThat(warmed).allSatisfy(loc -> assertThat(loc.validatedAt()).isEqualTo(WHEN));
    }

    @Test
    void theLoadIsBoundedByTheLimit() {
        FakeStore store = new FakeStore(List.of(
                new RtpColumn(WORLD, 100, 100, WHEN),
                new RtpColumn(WORLD, 200, 200, WHEN),
                new RtpColumn(WORLD, 300, 300, WHEN),
                new RtpColumn(WORLD, 400, 400, WHEN),
                new RtpColumn(WORLD, 500, 500, WHEN)));
        List<RtpSafeLocation> warmed = new ArrayList<>();

        prewarm(store).prewarm(area, 2, warmed::add);

        assertThat(store.lastLimit()).isEqualTo(2);
        assertThat(warmed).hasSize(2);
    }

    @Test
    void aNonPositiveLimitPrewarmsNothingAndTouchesNoStore() {
        FakeStore store = new FakeStore(List.of(new RtpColumn(WORLD, 100, 100, WHEN)));
        List<RtpSafeLocation> warmed = new ArrayList<>();

        prewarm(store).prewarm(area, 0, warmed::add);

        assertThat(warmed).isEmpty();
        assertThat(store.loadCalls()).isZero();
    }

    private RtpPoolPrewarm prewarm(FakeStore store) {
        AsyncSafeLocationFinder finder = new AsyncSafeLocationFinder(new ColumnChunkAccess(), policy, clock);
        return new RtpPoolPrewarm(store, finder, scheduler, new NoopLogger(), Duration.ofMillis(100));
    }

    /** A {@link ChunkAccess} that re-probes each column safe, except {@link #UNSAFE_X} which now has no headroom. */
    private static final class ColumnChunkAccess implements ChunkAccess {
        @Override
        public CompletableFuture<Optional<SafeCandidate>> probe(SafeSearchArea probeArea, int blockX, int blockZ) {
            boolean standingSafe = blockX != UNSAFE_X;
            SafeCandidate candidate = new SafeCandidate(
                    Position.of(WORLD, blockX + 0.5, 71.0, blockZ + 0.5),
                    BiomeName.of("plains"),
                    standingSafe,
                    false,
                    BlockTypeName.of("grass_block"));
            return CompletableFuture.completedFuture(Optional.of(candidate));
        }
    }

    /** A {@link RtpPoolStore} that returns a preset column list capped at the requested limit and counts loads. */
    private static final class FakeStore implements RtpPoolStore {
        private final List<RtpColumn> columns;
        private int loadCalls;
        private int lastLimit = -1;

        FakeStore(List<RtpColumn> columns) {
            this.columns = List.copyOf(columns);
        }

        int loadCalls() {
            return loadCalls;
        }

        int lastLimit() {
            return lastLimit;
        }

        @Override
        public void save(WorldRef world, java.util.Collection<RtpColumn> toSave) {}

        @Override
        public List<RtpColumn> load(WorldRef world, int limit) {
            loadCalls++;
            lastLimit = limit;
            return columns.stream().limit(limit).toList();
        }

        @Override
        public List<RtpColumn> loadByBiome(
                WorldRef world, com.uxplima.uxmessentials.teleport.domain.BiomeName biome, int limit) {
            return List.of();
        }

        @Override
        public int deleteStale(Duration olderThan) {
            return 0;
        }

        @Override
        public int count(WorldRef world) {
            return columns.size();
        }
    }

    /** A {@link Scheduler} that runs async and delayed work inline so the whole re-probe chain resolves in-test. */
    private static final class RecordingScheduler implements Scheduler {
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
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
