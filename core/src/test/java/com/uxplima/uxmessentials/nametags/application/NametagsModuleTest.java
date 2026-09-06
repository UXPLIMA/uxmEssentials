package com.uxplima.uxmessentials.nametags.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class NametagsModuleTest {

    @Test
    void identityMatchesItsContextPackage() {
        assertThat(new NametagsModule().id()).isEqualTo(ModuleId.of("nametags"));
        assertThat(new NametagsModule().configRoot()).isEqualTo("modules.nametags");
    }

    @Test
    void shipsDisabledByDefault() {
        NametagsModule module = new NametagsModule();

        // With no override the module is off: the above-head name is a surface a dedicated nametag plugin also
        // draws, so a fresh install does not claim it.
        assertThat(module.enabled(new FixedConfig(Map.of()))).isFalse();
        // An explicit enable in modules.conf turns it on, and the bundled format renders straight away.
        assertThat(module.enabled(new FixedConfig(Map.of("modules.nametags.enabled", true))))
                .isTrue();
    }

    @Test
    void publishesNoCommand() {
        // The nametag is always-on when enabled: there is no per-player visibility toggle.
        assertThat(new NametagsModule().commands()).isEmpty();
    }

    @Test
    void persistsNothingAndRegistersNoListenersInThePureModule() {
        NametagsModule module = new NametagsModule();

        assertThat(module.migrations()).isEmpty();
        // Bukkit-facing listeners land with the adapter, not the pure module.
        assertThat(module.listeners()).isEmpty();
    }

    /** A map-backed {@link ConfigStore} for driving the enable gate. */
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
}
