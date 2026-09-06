package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Pins the fault-tolerance of the self-rescheduling {@link ScoreboardRenderTask} loop: a tick body that throws must
 * never take the loop down with it. The task re-arms the next tick from inside the body, so before the guard a throw
 * would skip {@link ScoreboardRenderTask#start() the reschedule} and the scoreboard would silently stop updating until
 * a module reload. The scheduler is a capturing fake so each tick is fired by hand; MockBukkit is mocked only so the
 * happy tick's {@code Bukkit.getOnlinePlayers()} roster scan resolves to an empty set instead of throwing.
 */
class ScoreboardRenderTaskTest {

    private static final Duration INTERVAL = Duration.ofSeconds(1);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aTickWhoseBodyThrowsStillReschedulesAndLogsOneError() {
        RecordingScheduler scheduler = new RecordingScheduler();
        CountingLogger log = new CountingLogger();
        AnimationRegistry animations = mock(AnimationRegistry.class);
        // The global animation clock advance is the first thing the tick body does; make it blow up to stand in for
        // any RuntimeException escaping the synchronous tick body (a bad frame, a roster read, a per-entity dispatch).
        doThrow(new IllegalStateException("boom")).when(animations).advance();
        ScoreboardRenderTask task = new ScoreboardRenderTask(
                scheduler, mock(ScoreboardRenderer.class), animations, log, () -> INTERVAL, () -> true);

        task.start();
        scheduler.fireNext();

        assertThat(scheduler.pending)
                .as("the next tick is still armed after a throwing tick")
                .isNotNull();
        assertThat(scheduler.scheduleCount)
                .as("initial arm plus the re-arm from the throwing tick")
                .isEqualTo(2);
        assertThat(log.errors).as("the failure is logged exactly once").isEqualTo(1);
    }

    @Test
    void aHealthyTickStillReschedulesWithoutLogging() {
        RecordingScheduler scheduler = new RecordingScheduler();
        CountingLogger log = new CountingLogger();
        ScoreboardRenderTask task = new ScoreboardRenderTask(
                scheduler,
                mock(ScoreboardRenderer.class),
                mock(AnimationRegistry.class),
                log,
                () -> INTERVAL,
                () -> true);

        task.start();
        scheduler.fireNext(); // empty online set. The fan-out is a no-op, the tick completes cleanly

        assertThat(scheduler.pending).as("a clean tick re-arms as before").isNotNull();
        assertThat(scheduler.lastDelay).isEqualTo(INTERVAL);
        assertThat(log.errors).as("a clean tick logs nothing").isZero();
    }

    /** Captures the single pending {@code asyncAfter} task so the test drives each tick by hand. */
    private static final class RecordingScheduler implements Scheduler {
        private @Nullable Runnable pending;
        private @Nullable Duration lastDelay;
        private int scheduleCount;

        void fireNext() {
            Runnable next = pending;
            pending = null;
            if (next != null) {
                next.run();
            }
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            this.pending = task;
            this.lastDelay = delay;
            this.scheduleCount++;
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void async(Runnable task) {}
    }

    /** A logger that counts the error lines the guard emits. */
    private static final class CountingLogger implements Logger {
        private int errors;

        @Override
        public void error(String message, Throwable cause) {
            errors++;
        }

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
