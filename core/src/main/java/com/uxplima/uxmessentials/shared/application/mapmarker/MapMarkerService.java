package com.uxplima.uxmessentials.shared.application.mapmarker;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.event.WarpCreated;
import com.uxplima.uxmessentials.warps.domain.event.WarpDeleted;

/**
 * The map-markers integration's pure dispatch logic: it keeps a map plugin's marker layer in step with the
 * server's warps, spawns, and (opt-in) homes. {@link #refreshAll()} re-reads every enabled source and
 * re-publishes its markers, the on-enable and on-reload path, while {@link #onEvent(DomainEvent)} reacts to
 * a single warp/home create or delete so an individual marker is added or removed without re-rendering the
 * whole layer.
 *
 * <p>Every render decision folds {@link MapMarkerSettings#renders(MapMarkerKind)} first: a disabled master
 * switch or a kind whose toggle is off publishes nothing and reads no source, so the private-home source is
 * never touched while {@code homes} ships {@code false}. The actual map-plugin call is the
 * {@link MapMarkerPublisher}'s concern; this class stays free of any Bukkit or map-API type so it lives in
 * {@code :core} and is unit-tested against a fake publisher.
 */
public final class MapMarkerService {

    private final MapMarkerPublisher publisher;
    private final MapMarkerSettings settings;
    private final List<MapMarkerSource> sources;

    public MapMarkerService(MapMarkerPublisher publisher, MapMarkerSettings settings, List<MapMarkerSource> sources) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    }

    /** Re-read every enabled source and re-publish its markers (the on-enable / on-reload path). */
    public void refreshAll() {
        for (MapMarkerSource source : sources) {
            if (settings.renders(source.kind())) {
                publisher.publishAll(source.currentMarkers());
            }
        }
    }

    /** React to a single warp/home create or delete, adding or removing exactly that marker. */
    public void onEvent(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        switch (event) {
            case WarpCreated created -> publishWarp(created);
            case WarpDeleted deleted ->
                remove(MapMarkerKind.WARP, deleted.name().value());
            case HomeCreated created -> publishHome(created);
            case HomeDeleted deleted ->
                remove(
                        MapMarkerKind.HOME,
                        homeName(deleted.owner().uuid(), deleted.slot().index()));
            default -> {
                // Not a marker-bearing event; nothing to render.
            }
        }
    }

    private void publishWarp(WarpCreated created) {
        if (!settings.renders(MapMarkerKind.WARP)) {
            return;
        }
        String name = created.name().value();
        publisher.publish(marker(MapMarkerKind.WARP, name, created.location()));
    }

    private void publishHome(HomeCreated created) {
        if (!settings.renders(MapMarkerKind.HOME)) {
            return;
        }
        String name = homeName(created.owner().uuid(), created.slot().index());
        publisher.publish(marker(MapMarkerKind.HOME, name, created.location()));
    }

    private void remove(MapMarkerKind kind, String name) {
        if (!settings.renders(kind)) {
            return;
        }
        publisher.remove(MapMarker.idFor(kind, name));
    }

    private MapMarker marker(MapMarkerKind kind, String name, Position at) {
        return MapMarker.of(kind, name, settings.tooltipFor(name), at.world().name(), at.x(), at.y(), at.z());
    }

    /** A home's marker name namespaces the owner and slot so two players' home entries never collide. */
    private static String homeName(java.util.UUID owner, int slotIndex) {
        return owner + "/" + slotIndex;
    }
}
