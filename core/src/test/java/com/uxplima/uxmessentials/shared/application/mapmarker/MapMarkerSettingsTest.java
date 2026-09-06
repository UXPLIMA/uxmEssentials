package com.uxplima.uxmessentials.shared.application.mapmarker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class MapMarkerSettingsTest {

    @Test
    void defaults_enableWarpsAndSpawnsButNotHomes() {
        MapMarkerSettings settings = MapMarkerSettings.from(new FixedConfig(Map.of()));

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.warps()).isTrue();
        assertThat(settings.spawns()).isTrue();
        assertThat(settings.homes()).as("homes are private, off by default").isFalse();
        assertThat(settings.layerName()).isEqualTo("uxmEssentials");
        assertThat(settings.warpIcon()).isEqualTo("portal");
        assertThat(settings.spawnIcon()).isEqualTo("world");
    }

    @Test
    void renders_foldsMasterSwitchAndPerKindToggle() {
        MapMarkerSettings on = MapMarkerSettings.from(new FixedConfig(Map.of("map-markers.homes", true)));
        assertThat(on.renders(MapMarkerKind.WARP)).isTrue();
        assertThat(on.renders(MapMarkerKind.SPAWN)).isTrue();
        assertThat(on.renders(MapMarkerKind.HOME)).isTrue();

        MapMarkerSettings off = MapMarkerSettings.from(new FixedConfig(Map.of("map-markers.enabled", false)));
        assertThat(off.renders(MapMarkerKind.WARP))
                .as("master off overrides every kind")
                .isFalse();
        assertThat(off.renders(MapMarkerKind.SPAWN)).isFalse();
        assertThat(off.renders(MapMarkerKind.HOME)).isFalse();
    }

    @Test
    void renders_perKindToggleGatesIndependently() {
        MapMarkerSettings settings = MapMarkerSettings.from(new FixedConfig(Map.of("map-markers.warps", false)));
        assertThat(settings.renders(MapMarkerKind.WARP)).isFalse();
        assertThat(settings.renders(MapMarkerKind.SPAWN)).isTrue();
    }

    @Test
    void iconFor_returnsTheConfiguredIconPerKind() {
        MapMarkerSettings settings = MapMarkerSettings.from(new FixedConfig(Map.of(
                "map-markers.warp-icon", "compass",
                "map-markers.spawn-icon", "star",
                "map-markers.home-icon", "bighouse")));
        assertThat(settings.iconFor(MapMarkerKind.WARP)).isEqualTo("compass");
        assertThat(settings.iconFor(MapMarkerKind.SPAWN)).isEqualTo("star");
        assertThat(settings.iconFor(MapMarkerKind.HOME)).isEqualTo("bighouse");
    }

    @Test
    void tooltipFor_expandsTheNameToken() {
        MapMarkerSettings settings =
                MapMarkerSettings.from(new FixedConfig(Map.of("map-markers.tooltip", "Warp: <name>")));
        assertThat(settings.tooltipFor("shop")).isEqualTo("Warp: shop");
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
}
