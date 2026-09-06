package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the refresh-cadence logic of {@link HologramRefreshTask} against a fake clock (each {@link
 * HologramRefreshTask#tick()} stands in for one base period) and a recording refresh callback, so no live
 * server is needed: a refreshing hologram re-renders when its interval comes due, and a static one never does.
 * A dedicated case pins the per-element guard: one hologram that throws on refresh must not skip its siblings.
 */
class HologramRefreshTaskTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final int BASE_TICKS = 20;

    @Test
    void aRefreshingHologramReRendersWhenItsIntervalComesDue() {
        Hologram refreshing = hologram("clock", 40); // due every two base ticks
        List<Hologram> refreshed = new ArrayList<>();
        HologramRefreshTask task =
                new HologramRefreshTask(() -> List.of(refreshing), refreshed::add, new CountingLogger(), BASE_TICKS);

        task.tick(); // 20 ticks, not yet due
        assertThat(refreshed).isEmpty();
        task.tick(); // 40 ticks, due
        assertThat(refreshed).hasSize(1);
        task.tick(); // 60 ticks, not due
        task.tick(); // 80 ticks, due again
        assertThat(refreshed).hasSize(2);
    }

    @Test
    void aStaticHologramNeverReRenders() {
        Hologram still = hologram("sign", Hologram.STATIC);
        List<Hologram> refreshed = new ArrayList<>();
        HologramRefreshTask task =
                new HologramRefreshTask(() -> List.of(still), refreshed::add, new CountingLogger(), BASE_TICKS);

        for (int i = 0; i < 10; i++) {
            task.tick();
        }

        assertThat(refreshed).isEmpty();
    }

    @Test
    void aSubBaseIntervalRoundsUpToTheBaseCadence() {
        Hologram fast = hologram("fast", 5); // below base, refreshes every base tick instead
        List<Hologram> refreshed = new ArrayList<>();
        HologramRefreshTask task =
                new HologramRefreshTask(() -> List.of(fast), refreshed::add, new CountingLogger(), BASE_TICKS);

        task.tick();
        task.tick();

        assertThat(refreshed).hasSize(2);
    }

    @Test
    void oneThrowingHologramDoesNotSkipTheOthers() {
        Hologram first = hologram("first", BASE_TICKS);
        Hologram middle = hologram("middle", BASE_TICKS);
        Hologram last = hologram("last", BASE_TICKS);
        List<Hologram> refreshed = new ArrayList<>();
        CountingLogger log = new CountingLogger();
        Consumer<Hologram> refresh = hologram -> {
            if (hologram == middle) {
                throw new IllegalStateException("bad hologram");
            }
            refreshed.add(hologram);
        };
        HologramRefreshTask task =
                new HologramRefreshTask(() -> List.of(first, middle, last), refresh, log, BASE_TICKS);

        task.tick(); // all three are due this tick; the middle one throws

        assertThat(refreshed)
                .as("the throwing middle hologram does not abort its siblings")
                .containsExactly(first, last);
        assertThat(log.errors).as("the one bad refresh is logged").isEqualTo(1);
    }

    private static Hologram hologram(String name, int intervalTicks) {
        return Hologram.create(
                        HologramName.of(name),
                        Position.of(WORLD, 0, 64, 0),
                        List.of(new HologramLine("line")),
                        Instant.EPOCH)
                .withRefreshIntervalTicks(intervalTicks);
    }

    /** A logger that counts the error lines the per-element guard emits. */
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
