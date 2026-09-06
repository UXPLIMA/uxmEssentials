package com.uxplima.uxmessentials.shared.application.mapmarker;

import java.util.List;

/**
 * Supplies the full current marker set for one {@link MapMarkerKind} when the integration does a refresh
 * (on enable and on reload). The adapter implements this over the warps repository, the spawn directory, or
 * the homes repository, mapping each stored location to a {@link MapMarker} with the configured icon/tooltip
 * already folded in. A kind whose toggle is off is never asked. The {@link MapMarkerService} consults
 * {@link MapMarkerSettings#renders} before it reads a source, so a private-home source is never read while
 * the {@code homes} toggle ships {@code false}.
 *
 * <p>Reading the source may touch the database (warps/homes/spawns are DB-backed), so the service calls it
 * off the main thread; the contract here is a plain synchronous read of the current set.
 */
public interface MapMarkerSource {

    /** Which kind of marker this source produces. */
    MapMarkerKind kind();

    /** The full current marker set for this kind, already resolved to {@link MapMarker}s. */
    List<MapMarker> currentMarkers();
}
