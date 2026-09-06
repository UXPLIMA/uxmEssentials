package com.uxplima.uxmessentials.shared.application.mapmarker;

import java.util.Objects;

/**
 * A single point to render on a web map: a stable {@link #id() id}, the {@link #kind() kind} that picks the
 * icon and id-namespace, the operator-facing {@link #label() label} (the tooltip), and the world name plus
 * coordinates the map plugin places the marker at. This is the kernel's map primitive. It carries a world
 * <em>name</em> (the string the map plugins address worlds by) rather than a Bukkit {@code World}, so the
 * value stays free of any platform or map-API type and the adapter resolves it at the boundary.
 *
 * <p>The id is derived once at construction from the kind and the source name ({@code warp:shop}), so a
 * publisher can {@code remove(id)} a single marker on a delete without re-rendering the whole layer, and a
 * re-publish under the same id replaces in place rather than duplicating.
 *
 * @param id the stable marker id, unique within the layer ({@code <kind>:<name>})
 * @param kind what the marker represents (icon + id namespace)
 * @param label the operator-facing tooltip text
 * @param world the world name the map plugin addresses the world by
 * @param x world x coordinate
 * @param y world y coordinate
 * @param z world z coordinate
 */
public record MapMarker(String id, MapMarkerKind kind, String label, String world, double x, double y, double z) {

    public MapMarker {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(world, "world");
        if (id.isBlank()) {
            throw new IllegalArgumentException("marker id must not be blank");
        }
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    /**
     * Build a marker for {@code kind} at {@code name}, deriving the stable id from the kind and the name. The
     * label is resolved by the caller from the configured tooltip template; the name is what the id is keyed
     * on, so the same name under a different kind yields a distinct marker.
     */
    public static MapMarker of(
            MapMarkerKind kind, String name, String label, String world, double x, double y, double z) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        return new MapMarker(idFor(kind, name), kind, label, world, x, y, z);
    }

    /** The stable marker id a {@code kind}/{@code name} pair maps to ({@code warp:shop}). */
    public static String idFor(MapMarkerKind kind, String name) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        return kind.name().toLowerCase(java.util.Locale.ROOT) + ':' + name;
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
