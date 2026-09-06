package com.uxplima.uxmessentials.regions.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.regions.adapter.outbound.NoWorldGuardRegionService;
import com.uxplima.uxmessentials.regions.adapter.outbound.WorldGuardRegionService;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Covers the soft-dependency probe {@link RegionsWiring#regionService}: with no WorldGuard plugin present it binds
 * the inert no-op that reports {@code available() == false}; with a WorldGuard plugin present it binds the real
 * reflective implementation whose {@code available()} follows the plugin's enabled state. WorldGuard itself is not
 * on the test classpath, so the "present" case is a plugin-manager registration only, enough to prove the probe
 * selects the real implementation.
 */
class RegionsWiringTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bindsTheNoOpWhenWorldGuardIsAbsent() {
        RegionService service = RegionsWiring.regionService(server, new NoopLogger());

        assertThat(service).isInstanceOf(NoWorldGuardRegionService.class);
        assertThat(service.available()).isFalse();
    }

    @Test
    void bindsTheWorldGuardImplementationWhenThePluginIsPresent() {
        MockBukkit.createMockPlugin("WorldGuard");

        RegionService service = RegionsWiring.regionService(server, new NoopLogger());

        assertThat(service).isInstanceOf(WorldGuardRegionService.class);
        assertThat(service.available()).isTrue();
    }

    /** A {@link Logger} that discards everything; the probe only needs a non-null one to hand the real impl. */
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
