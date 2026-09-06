package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link PoseModule} feature-module contract: it reports the {@code poses} id, ships enabled by default,
 * honours an explicit {@code modules.poses.enabled = false}, and, as the Phase-0 skeleton, contributes no command,
 * listener, or migration. The registry-level wiring is covered by {@code FeatureModuleRegistryDriftTest}.
 */
class PoseModuleTest {

    @Test
    void reportsItsIdAndConfigRoot() {
        PoseModule module = new PoseModule();

        assertThat(module.id()).isEqualTo(ModuleId.of("poses"));
        assertThat(module.configRoot()).isEqualTo("modules.poses");
    }

    @Test
    void shipsEnabledByDefaultAndHonoursAnExplicitOptOut() {
        PoseModule module = new PoseModule();

        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        assertThat(module.enabled(new FixedConfig(Map.of("modules.poses.enabled", false))))
                .isFalse();
    }

    @Test
    void phaseZeroContributesNoCommandListenerOrMigration() {
        PoseModule module = new PoseModule();

        assertThat(module.commands()).isEmpty();
        assertThat(module.listeners()).isEmpty();
        assertThat(module.migrations()).isEmpty();
    }

    @Test
    void startAndStopTrackTheRunningFlag() {
        PoseModule module = new PoseModule();
        assertThat(module.isRunning()).isFalse();

        module.stop();
        assertThat(module.isRunning()).isFalse();
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
