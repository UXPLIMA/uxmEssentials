package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards that a fresh first-run extraction produces a layout the {@link ConfigurateConfigStore} resolves to the
 * bundled per-module defaults. A sibling file ({@code rtp.conf}), a one-file module ({@code homes}), and a
 * global from the root {@code config.conf}. Lives beside {@link DefaultResources} because its writer is
 * package-private.
 */
class ConfigLayoutFirstRunDriftTest {

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    @Test
    void firstRunTreeResolvesAPerModuleKey(@TempDir Path dir) {
        DefaultResources.writeInto(dir, java.util.logging.Logger.getLogger("t"), "test");
        ConfigurateConfigStore store = ConfigurateConfigStore.loadLayout(dir, NOOP);
        assertThat(store.getInt("modules.teleport.rtp.min-radius", -1)).isEqualTo(100);
        assertThat(store.getInt("modules.homes.default-limit", -1)).isEqualTo(3);
        assertThat(store.getString("storage.backend", "x")).isEqualTo("sqlite");
    }

    @Test
    void firstRunTreeSurfacesTheDiscordlinkHostConfig(@TempDir Path dir) {
        DefaultResources.writeInto(dir, java.util.logging.Logger.getLogger("t"), "test");
        ConfigurateConfigStore store = ConfigurateConfigStore.loadLayout(dir, NOOP);
        // discordlink ships off (it needs a bot token to do anything), so what this proves is that the module's own
        // file is mounted at all: its switch reads back false rather than falling through to the fallback.
        assertThat(store.getBoolean("modules.discordlink.enabled", true)).isFalse();
        assertThat(store.getInt("modules.discordlink.code-ttl-seconds", -1)).isEqualTo(600);
    }
}
