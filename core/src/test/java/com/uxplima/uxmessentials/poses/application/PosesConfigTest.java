package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the poses module's typed config view: the shipped defaults an operator inherits by deleting a line, and the
 * one non-obvious default. {@code features.player-sit} ships OFF because it is the one verb that touches a second
 * player: plus that an explicit override is read back.
 */
class PosesConfigTest {

    @Test
    void defaultsMatchTheShippedConfig() {
        PosesConfig config = PosesConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.sitOnBlocks()).isTrue();
        assertThat(config.maxDistance()).isEqualTo(2.0);
        assertThat(config.returnToStart()).isTrue();
        assertThat(config.snore()).isTrue();
        assertThat(config.respectClaims()).isTrue();
        assertThat(config.respectWorldguard()).isTrue();
        assertThat(config.sittableMaterials()).containsExactly("*_STAIRS", "*_SLAB", "*_CARPET");
    }

    @Test
    void playerSitDefaultsOffWhileTheOtherVerbsDefaultOn() {
        PosesConfig.Features features =
                PosesConfig.from(new FixedConfig(Map.of())).features();

        assertThat(features.playerSit()).isFalse();
        assertThat(features.sit()).isTrue();
        assertThat(features.lay()).isTrue();
        assertThat(features.bellyflop()).isTrue();
        assertThat(features.spin()).isTrue();
        assertThat(features.crawl()).isTrue();
    }

    @Test
    void explicitOverridesAreReadBack() {
        PosesConfig config = PosesConfig.from(new FixedConfig(Map.of(
                "enabled", false,
                "features.player-sit", true,
                "features.crawl", false,
                "sit-on-blocks", false,
                "return-to-start", false)));

        assertThat(config.enabled()).isFalse();
        assertThat(config.features().playerSit()).isTrue();
        assertThat(config.features().crawl()).isFalse();
        assertThat(config.sitOnBlocks()).isFalse();
        assertThat(config.returnToStart()).isFalse();
    }

    /** A map-backed {@link ConfigStore} addressing keys by their dotted path relative to the module root. */
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
