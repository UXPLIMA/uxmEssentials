package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Drives the pre-generation engine through its injectable seams. The chunk source completes every request
 * synchronously and the scheduler captures the repeating loop's {@code Runnable} per {@code start}, so a
 * test can advance the loop one tick at a time and observe the whole orchestration, start, the
 * already-running guard, draining to completion with the DONE notice, cancellation without a notice, and
 * bulk stop, without a live server or a real clock. The real {@code World#getChunkAtAsync} is exercised
 * only against a running Paper server; here the seam stands in for it deterministically.
 */
class BukkitWorldPregenTest {

    private static final WorldName WORLD = WorldName.of("w");
    private static final WorldName OTHER = WorldName.of("w2");
    private static final PlayerRef INITIATOR = new PlayerRef(UUID.randomUUID(), "op");

    private CapturingScheduler scheduler;
    private RecordingSink sink;
    private BukkitWorldPregen pregen;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        scheduler = new CapturingScheduler();
        sink = new RecordingSink();
        Notifier notifier = new Notifier(new StubMessages(), sink);
        WorldsSettings settings = new WorldsSettings(new MapConfig());
        pregen = new BukkitWorldPregen(
                new ImmediateGenSource(),
                scheduler,
                new FakeWorldEngine(),
                new StubMessages(),
                notifier,
                settings,
                new NoOpLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void startRegistersAJobAndCapturesTheLoop() {
        Result<Unit, WorldError> result = pregen.start(INITIATOR, WORLD, 1);

        assertThat(result.isOk()).isTrue();
        assertThat(pregen.isRunning(WORLD)).isTrue();
        CapturedLoop loop = scheduler.last();
        assertThat(loop.task).isNotNull();
        assertThat(loop.initialDelay).isEqualTo(Duration.ZERO);
    }

    @Test
    void secondStartOnSameWorldIsRefusedAndLeavesTheFirstJobIntact() {
        pregen.start(INITIATOR, WORLD, 1);
        CapturedLoop first = scheduler.last();

        Result<Unit, WorldError> result = pregen.start(INITIATOR, WORLD, 1);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.PREGEN_ALREADY_RUNNING);
        assertThat(pregen.isRunning(WORLD)).isTrue();
        assertThat(scheduler.loops).hasSize(1); // no second loop was scheduled
        assertThat(first.handle.closed).isFalse();
    }

    @Test
    void drivingTheLoopToExhaustionFinishesAndNotifiesDone() {
        pregen.start(INITIATOR, WORLD, 1); // radius 1 => 9 chunks, immediate gen, cap 10
        CapturedLoop loop = scheduler.last();

        // One tick tops up and drains all 9 chunks: because the fake source completes each request inline,
        // every chunk is counted before the loop moves on, so the same tick observes the iterator empty
        // with nothing in flight and finishes.
        loop.task.run();

        assertThat(pregen.isRunning(WORLD)).isFalse();
        assertThat(sink.delivered).containsExactly(WorldsMessageKey.WORLD_PREGEN_DONE.key());
        assertThat(loop.handle.closed).isTrue();
    }

    @Test
    void cancelOfARunningJobStopsItWithoutNotifying() {
        pregen.start(INITIATOR, WORLD, 1);
        CapturedLoop loop = scheduler.last();

        boolean cancelled = pregen.cancel(WORLD);

        assertThat(cancelled).isTrue();
        assertThat(pregen.isRunning(WORLD)).isFalse();
        assertThat(loop.handle.closed).isTrue();
        assertThat(sink.delivered).isEmpty();
    }

    @Test
    void cancelOfAnIdleWorldReturnsFalse() {
        assertThat(pregen.cancel(WORLD)).isFalse();
    }

    @Test
    void stopAllCancelsEveryRunningJob() {
        pregen.start(INITIATOR, WORLD, 1);
        CapturedLoop a = scheduler.last();
        pregen.start(INITIATOR, OTHER, 1);
        CapturedLoop b = scheduler.last();

        pregen.stopAll();

        assertThat(pregen.isRunning(WORLD)).isFalse();
        assertThat(pregen.isRunning(OTHER)).isFalse();
        assertThat(a.handle.closed).isTrue();
        assertThat(b.handle.closed).isTrue();
    }

    /** A chunk source that resolves every request immediately, so the completion callback runs inline. */
    private static final class ImmediateGenSource implements ChunkGenSource {
        @Override
        public CompletableFuture<?> generate(WorldName world, int chunkX, int chunkZ) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /** The {@code Runnable} + cancel handle + initial delay captured from one {@code repeatGlobal} call. */
    private static final class CapturedLoop {
        final Runnable task;
        final RecordingHandle handle = new RecordingHandle();
        final Duration initialDelay;

        CapturedLoop(Runnable task, Duration initialDelay) {
            this.task = task;
            this.initialDelay = initialDelay;
        }
    }

    /**
     * Captures every repeating loop in call order and runs entity work inline, so the engine's show/hide
     * bar and DONE notification fire synchronously. The test advances a loop by invoking its captured task.
     */
    private static final class CapturingScheduler implements Scheduler {

        final List<CapturedLoop> loops = new ArrayList<>();

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            CapturedLoop loop = new CapturedLoop(task, initialDelay);
            loops.add(loop);
            return loop.handle;
        }

        CapturedLoop last() {
            return loops.get(loops.size() - 1);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
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
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** An {@link AutoCloseable} that records whether the engine cancelled the loop. */
    private static final class RecordingHandle implements AutoCloseable {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    /** A world engine that only needs to answer the spawn-point read the engine consults. */
    private static final class FakeWorldEngine implements WorldEngine {
        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.of(Position.of(new WorldRef(UUID.randomUUID(), name.value()), 0, 64, 0));
        }

        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return true;
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return true;
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.of();
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.empty();
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public int playerCount(WorldName name) {
            return 0;
        }
    }

    /** Resolves any key to its catalog string, so MiniMessage can deserialise it and the test can assert it. */
    private static final class StubMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records every delivered (already-resolved) string, in order. */
    private static final class RecordingSink implements MessageSink {
        final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    /** A config store with no overrides, so {@link WorldsSettings} returns its defaults (cap 10, 1-tick period). */
    private static final class MapConfig implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoOpLogger implements Logger {
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
