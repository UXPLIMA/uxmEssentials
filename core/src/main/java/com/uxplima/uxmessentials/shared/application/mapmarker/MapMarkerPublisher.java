package com.uxplima.uxmessentials.shared.application.mapmarker;

import java.util.Collection;

/**
 * Outbound port for rendering {@link MapMarker}s onto a web map ({@code Dynmap}, {@code squaremap}). The
 * application drives this narrow contract; the adapter behind it owns the single map-plugin layer and
 * translates each call into that plugin's marker API past its plugin-present guard. A server with no map
 * plugin binds the {@link #noOp() no-op} implementation, so the publish path is always present and the
 * caller never branches on whether a map plugin is installed.
 *
 * <p>The icon a kind renders with, the layer name, and the tooltip template are the adapter's concern, fixed
 * at construction from {@link MapMarkerSettings}; the port carries only the resolved {@link MapMarker} so the
 * application stays free of any icon-id or layer detail.
 */
public interface MapMarkerPublisher {

    /** Render {@code marker}, replacing any existing marker under the same {@link MapMarker#id() id}. */
    void publish(MapMarker marker);

    /** Render every marker in {@code markers}; equivalent to {@link #publish} per element. */
    void publishAll(Collection<MapMarker> markers);

    /** Remove the marker under {@code markerId}; a no-op when no such marker is on the map. */
    void remove(String markerId);

    /** Remove every marker this plugin owns on the map (the whole layer's content), e.g. on disable. */
    void clear();

    /** A publisher that renders nothing: the binding when no supported map plugin is present. */
    static MapMarkerPublisher noOp() {
        return NoOpMapMarkerPublisher.INSTANCE;
    }
}
