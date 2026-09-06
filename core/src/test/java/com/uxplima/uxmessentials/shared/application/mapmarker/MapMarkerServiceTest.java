package com.uxplima.uxmessentials.shared.application.mapmarker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.event.WarpCreated;
import com.uxplima.uxmessentials.warps.domain.event.WarpDeleted;
import org.junit.jupiter.api.Test;

class MapMarkerServiceTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void refreshAll_publishesOneMarkerPerWarpAndSpawn_butNoHomesByDefault() {
        RecordingPublisher publisher = new RecordingPublisher();
        MapMarkerService service = new MapMarkerService(
                publisher,
                settings(Map.of()),
                List.of(
                        source(MapMarkerKind.WARP, marker(MapMarkerKind.WARP, "shop")),
                        source(MapMarkerKind.SPAWN, marker(MapMarkerKind.SPAWN, "world")),
                        source(MapMarkerKind.HOME, marker(MapMarkerKind.HOME, ALICE.uuid() + "/base"))));

        service.refreshAll();

        assertThat(publisher.published)
                .extracting(MapMarker::kind)
                .containsExactly(MapMarkerKind.WARP, MapMarkerKind.SPAWN);
        assertThat(publisher.published).noneMatch(m -> m.kind() == MapMarkerKind.HOME);
    }

    @Test
    void refreshAll_publishesHomes_whenHomesToggleOn() {
        RecordingPublisher publisher = new RecordingPublisher();
        MapMarkerService service = new MapMarkerService(
                publisher,
                settings(Map.of("map-markers.homes", true)),
                List.of(source(MapMarkerKind.HOME, marker(MapMarkerKind.HOME, ALICE.uuid() + "/base"))));

        service.refreshAll();

        assertThat(publisher.published).extracting(MapMarker::kind).containsExactly(MapMarkerKind.HOME);
    }

    @Test
    void onEvent_warpCreated_publishesItsMarker() {
        RecordingPublisher publisher = new RecordingPublisher();
        MapMarkerService service = new MapMarkerService(publisher, settings(Map.of()), List.of());

        service.onEvent(new WarpCreated(WarpName.of("shop"), ALICE, Position.of(WORLD, 1, 64, 2)));

        assertThat(publisher.published).singleElement().satisfies(m -> {
            assertThat(m.id()).isEqualTo("warp:shop");
            assertThat(m.kind()).isEqualTo(MapMarkerKind.WARP);
            assertThat(m.world()).isEqualTo("world");
            assertThat(m.x()).isEqualTo(1);
        });
    }

    @Test
    void onEvent_warpDeleted_removesItsMarker() {
        RecordingPublisher publisher = new RecordingPublisher();
        MapMarkerService service = new MapMarkerService(publisher, settings(Map.of()), List.of());

        service.onEvent(new WarpDeleted(WarpName.of("shop"), ALICE));

        assertThat(publisher.removed).containsExactly("warp:shop");
    }

    @Test
    void onEvent_homeCreated_noMarker_whenHomesOff() {
        RecordingPublisher publisher = new RecordingPublisher();
        MapMarkerService service = new MapMarkerService(publisher, settings(Map.of()), List.of());

        service.onEvent(new HomeCreated(ALICE, HomeSlot.of(0), Position.of(WORLD, 5, 64, 6)));

        assertThat(publisher.published).as("homes off, no private home marker").isEmpty();
    }

    @Test
    void onEvent_homeCreated_publishesNamespacedMarker_whenHomesOn() {
        RecordingPublisher publisher = new RecordingPublisher();
        MapMarkerService service =
                new MapMarkerService(publisher, settings(Map.of("map-markers.homes", true)), List.of());

        service.onEvent(new HomeCreated(ALICE, HomeSlot.of(0), Position.of(WORLD, 5, 64, 6)));

        assertThat(publisher.published).singleElement().satisfies(m -> {
            assertThat(m.kind()).isEqualTo(MapMarkerKind.HOME);
            assertThat(m.id()).isEqualTo("home:" + ALICE.uuid() + "/0");
        });
    }

    @Test
    void onEvent_homeDeleted_removesNamespacedMarker_whenHomesOn() {
        RecordingPublisher publisher = new RecordingPublisher();
        MapMarkerService service =
                new MapMarkerService(publisher, settings(Map.of("map-markers.homes", true)), List.of());

        service.onEvent(new HomeDeleted(ALICE, HomeSlot.of(0)));

        assertThat(publisher.removed).containsExactly("home:" + ALICE.uuid() + "/0");
    }

    @Test
    void refreshAll_skipsDisabledKinds_andReadsNoSourceForThem() {
        RecordingPublisher publisher = new RecordingPublisher();
        ThrowingSource homeSource = new ThrowingSource();
        MapMarkerService service =
                new MapMarkerService(publisher, settings(Map.of("map-markers.warps", false)), List.of(homeSource));

        service.refreshAll();

        assertThat(homeSource.read)
                .as("a disabled kind's source must never be read")
                .isFalse();
        assertThat(publisher.published).isEmpty();
    }

    private static MapMarkerSettings settings(Map<String, Object> values) {
        return MapMarkerSettings.from(new FixedConfig(values));
    }

    private static MapMarker marker(MapMarkerKind kind, String name) {
        return MapMarker.of(kind, name, name, "world", 0, 64, 0);
    }

    private static MapMarkerSource source(MapMarkerKind kind, MapMarker marker) {
        return new MapMarkerSource() {
            @Override
            public MapMarkerKind kind() {
                return kind;
            }

            @Override
            public List<MapMarker> currentMarkers() {
                return List.of(marker);
            }
        };
    }

    /** A source whose {@link #currentMarkers()} fails if read: proves a disabled kind is never queried. */
    private static final class ThrowingSource implements MapMarkerSource {
        private boolean read;

        @Override
        public MapMarkerKind kind() {
            return MapMarkerKind.WARP;
        }

        @Override
        public List<MapMarker> currentMarkers() {
            read = true;
            throw new AssertionError("disabled source was read");
        }
    }

    private static final class RecordingPublisher implements MapMarkerPublisher {
        private final List<MapMarker> published = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();

        @Override
        public void publish(MapMarker marker) {
            published.add(marker);
        }

        @Override
        public void publishAll(Collection<MapMarker> markers) {
            published.addAll(markers);
        }

        @Override
        public void remove(String markerId) {
            removed.add(markerId);
        }

        @Override
        public void clear() {
            published.clear();
            removed.clear();
        }
    }

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
