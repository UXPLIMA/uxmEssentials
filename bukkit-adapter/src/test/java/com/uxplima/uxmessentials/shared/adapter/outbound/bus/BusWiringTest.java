package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * Lifecycle coverage of {@link BusWiring} on the disabled path. The property the heartbeat must honour: a
 * backend with {@code network.enabled = false} runs purely local and starts no heartbeat. The disabled branch
 * returns before any transport is built, so a bare Mockito {@link Plugin} stands in (it is never touched), and a
 * scheduler that records every {@code asyncAfter} proves the start/stop hooks schedule nothing.
 */
class BusWiringTest {

    @Test
    void aDisabledBackendHasNoPeerRosterAndStartsNoHeartbeat() {
        RecordingScheduler scheduler = new RecordingScheduler();
        BusWiring.Wired wired = BusWiring.wire(
                mock(Plugin.class), new FixedConfig(Map.of("network.enabled", false)), scheduler, new SilentLogger());

        // No roster exists for a standalone backend, so the cluster-peer check is never registered for it.
        assertThat(wired.peers()).isEmpty();

        // The lifecycle hooks are no-ops for a disabled backend: nothing is ever scheduled.
        wired.start();
        wired.stop();
        assertThat(scheduler.asyncAfterCalls).isZero();
    }

    /** A scheduler that counts deferred-task scheduling so the test can assert the disabled path schedules none. */
    private static final class RecordingScheduler implements Scheduler {

        private int asyncAfterCalls;

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            asyncAfterCalls++;
        }

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}
    }

    /** A map-backed {@link ConfigStore} addressing keys by their absolute dotted path. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }

    /** A logger that discards every line; this test asserts scheduling, not log output. */
    private static final class SilentLogger implements Logger {

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
