/**
 * The map-markers integration's pure application surface: the {@link
 * com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerPublisher} outbound port and its no-op
 * binding, the {@link com.uxplima.uxmessentials.shared.application.mapmarker.MapMarker} value, the {@link
 * com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings} read from the root {@code
 * map-markers} config block, the {@link com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSource}
 * a refresh reads, and the {@link com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerService}
 * that keeps a web-map layer in step with the server's warps, spawns, and opt-in homes.
 *
 * <p>Everything here is map-API- and Bukkit-free so it lives in {@code :core}; the Dynmap and squaremap
 * adapters that satisfy the publisher port, and the spawn/warp/home sources, live in the bukkit-adapter.
 * Homes are off by default: per-player home locations are private, so surfacing them on a public map is a
 * deliberate operator opt-in (see {@code MapMarkerSettings#homes}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.application.mapmarker;
