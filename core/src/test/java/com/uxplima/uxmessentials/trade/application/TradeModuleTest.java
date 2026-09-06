package com.uxplima.uxmessentials.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link TradeModule} feature-module contract: it reports the {@code trade} id, ships enabled by default,
 * honours an explicit {@code modules.trade.enabled = false}, and, as the Phase-1 skeleton, contributes no command,
 * listener, or migration. The registry-level wiring is covered by {@code FeatureModuleRegistryDriftTest}.
 */
class TradeModuleTest {

    @Test
    void reportsItsIdAndConfigRoot() {
        TradeModule module = new TradeModule();

        assertThat(module.id()).isEqualTo(ModuleId.of("trade"));
        assertThat(module.configRoot()).isEqualTo("modules.trade");
    }

    @Test
    void shipsEnabledByDefaultAndHonoursAnExplicitOptOut() {
        TradeModule module = new TradeModule();

        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        assertThat(module.enabled(new FixedConfig(Map.of("modules.trade.enabled", false))))
                .isFalse();
    }

    @Test
    void phaseOneContributesNoCommandListenerOrMigration() {
        TradeModule module = new TradeModule();

        assertThat(module.commands()).isEmpty();
        assertThat(module.listeners()).isEmpty();
        assertThat(module.migrations()).isEmpty();
    }

    @Test
    void startAndStopTrackTheRunningFlag() {
        TradeModule module = new TradeModule();
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
