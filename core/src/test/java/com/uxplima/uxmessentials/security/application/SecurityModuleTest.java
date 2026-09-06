package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link SecurityModule} feature-module contract: it reports the {@code security} id, ships enabled by
 * default, honours an explicit {@code modules.security.enabled = false}, and, as the Phase-1 skeleton, declares no
 * CommandSpec, listener or migration (the {@code /2fa} and {@code /pin} verbs are Brigadier registrations from the
 * adapter, and the {@code security_2fa} table is in the persistence baseline). Registry wiring is covered by
 * {@code FeatureModuleRegistryDriftTest}.
 */
class SecurityModuleTest {

    @Test
    void reportsItsIdAndConfigRoot() {
        SecurityModule module = new SecurityModule();

        assertThat(module.id()).isEqualTo(ModuleId.of("security"));
        assertThat(module.configRoot()).isEqualTo("modules.security");
    }

    @Test
    void shipsEnabledByDefaultAndHonoursAnExplicitOptOut() {
        SecurityModule module = new SecurityModule();

        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        assertThat(module.enabled(new FixedConfig(Map.of("modules.security.enabled", false))))
                .isFalse();
    }

    @Test
    void phaseOneDeclaresNoCommandSpecListenerOrMigration() {
        SecurityModule module = new SecurityModule();

        assertThat(module.commands()).isEmpty();
        assertThat(module.listeners()).isEmpty();
        assertThat(module.migrations()).isEmpty();
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
