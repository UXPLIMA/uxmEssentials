package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarker;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerKind;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerPublisher;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * The map-plugin discoverer's plugin-present guard: with none of squaremap, Dynmap or BlueMap installed the
 * integration binds the no-op publisher and reads no map-plugin SDK, so the plugin runs fully without any map
 * plugin, the same soft-depend contract the economy and PlaceholderAPI adapters honour. Loading a real map
 * plugin into the test would pull in classes a stock server never has, so this exercises the absent-plugin path
 * directly.
 *
 * <p>The BlueMap cases go one step further than absence: BlueMap is <em>installed</em> but its SDK is off the
 * test classpath, which is exactly the shape of a server running an incompatible BlueMap. The publisher must
 * degrade to rendering nothing rather than throwing into the caller.
 */
class MapMarkerPublishersTest {

    @Test
    void anyPresent_isFalse_whenNoMapPluginInstalled() {
        Server server = serverWithoutMapPlugins();
        assertThat(MapMarkerPublishers.anyPresent(server)).isFalse();
    }

    @Test
    void discover_returnsNoOpPublisher_whenNoMapPluginInstalled() {
        Server server = serverWithoutMapPlugins();
        MapMarkerPublisher publisher = MapMarkerPublishers.discover(server, settings(), noOpLog());

        // The no-op publisher renders nothing and never touches a map-plugin SDK; calling it must not throw.
        publisher.publish(MapMarker.of(MapMarkerKind.WARP, "shop", "shop", "world", 1, 64, 2));
        publisher.publishAll(List.of());
        publisher.remove("warp:shop");
        publisher.clear();
        assertThat(publisher).isSameAs(MapMarkerPublisher.noOp());
    }

    @Test
    void anyPresent_isTrue_whenOnlyBlueMapInstalled() {
        assertThat(MapMarkerPublishers.anyPresent(serverWithBlueMap())).isTrue();
    }

    @Test
    void discover_bindsBlueMap_whenItIsTheOnlyMapPluginInstalled() {
        MapMarkerPublisher publisher = MapMarkerPublishers.discover(serverWithBlueMap(), settings(), noOpLog());

        assertThat(publisher).isNotSameAs(MapMarkerPublisher.noOp());
    }

    @Test
    void theBlueMapPublisherDegradesWhenTheSdkIsUnreachable() {
        MapMarkerPublisher publisher = MapMarkerPublishers.discover(serverWithBlueMap(), settings(), noOpLog());

        // BlueMap's API is not on the test classpath, so every call takes the degrade path. None may throw:
        // a broken map integration must not take a warp creation or a plugin disable down with it.
        publisher.publish(MapMarker.of(MapMarkerKind.WARP, "shop", "shop", "world", 1, 64, 2));
        publisher.publishAll(List.of(MapMarker.of(MapMarkerKind.SPAWN, "spawn", "spawn", "world", 0, 64, 0)));
        publisher.remove("warp:shop");
        publisher.clear();
    }

    private static Server serverWithoutMapPlugins() {
        Server server = mock(Server.class);
        PluginManager pm = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pm);
        when(pm.getPlugin("squaremap")).thenReturn(null);
        when(pm.getPlugin("dynmap")).thenReturn(null);
        when(pm.getPlugin("BlueMap")).thenReturn(null);
        return server;
    }

    private static Server serverWithBlueMap() {
        Server server = mock(Server.class);
        PluginManager pm = mock(PluginManager.class);
        Plugin blueMap = mock(Plugin.class);
        when(blueMap.isEnabled()).thenReturn(true);
        when(server.getPluginManager()).thenReturn(pm);
        when(pm.getPlugin("squaremap")).thenReturn(null);
        when(pm.getPlugin("dynmap")).thenReturn(null);
        when(pm.getPlugin("BlueMap")).thenReturn(blueMap);
        return server;
    }

    private static MapMarkerSettings settings() {
        return MapMarkerSettings.from(new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                return fallback;
            }
        });
    }

    private static Logger noOpLog() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }
}
