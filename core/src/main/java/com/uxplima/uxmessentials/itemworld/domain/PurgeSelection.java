package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The selection criteria for an entity-purge verb. {@code /butcher [radius]}, {@code /killall [type]} and
 * {@code /remove <type> [radius]}. A purge is the most abusable itemworld verb (it can clear a region or a
 * whole world of entities), so the selection is a validated domain value the use case audit-logs before the
 * adapter sweeps.
 *
 * <p>The selection composes three independent filters: a {@link Scope} (a bounded radius around the actor, or
 * the whole world), an optional entity-type filter (empty means "all hostile mobs" for {@code /butcher}, or
 * the named type for {@code /remove}//{@code /killall}), and a {@link Category} that bounds <em>what</em> may
 * be swept (never players; named/tamed/owned exclusions are honoured by the adapter). A radius scope clamps
 * to a maximum so {@code /remove zombie 1000000} cannot stall the server.
 *
 * @param scope whether the sweep is radius-bounded or world-wide
 * @param radius the radius in blocks for a {@link Scope#RADIUS} sweep, ignored for {@link Scope#WORLD}
 * @param typeId the entity-type filter, empty for "the whole category"
 * @param category which class of entity the sweep may touch
 */
public record PurgeSelection(Scope scope, int radius, Optional<String> typeId, Category category) {

    /** The largest radius a purge may sweep when an operator leaves {@code purge-max-radius} unset. */
    public static final int DEFAULT_MAX_RADIUS = 256;

    /** Whether a purge is bounded to a radius around the actor or sweeps the whole world. */
    public enum Scope {
        RADIUS,
        WORLD
    }

    /** The class of entity a purge may remove: never players. */
    public enum Category {
        /** Hostile mobs only, the {@code /butcher} default. */
        MONSTERS,
        /** A single named entity type, {@code /remove <type>}, {@code /killall <type>}. */
        NAMED_TYPE,
        /** Every removable entity (drops, mobs, projectiles), the {@code /killall} all-form. */
        ALL_ENTITIES
    }

    public PurgeSelection {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(category, "category");
        if (scope == Scope.RADIUS && radius < 1) {
            throw new IllegalArgumentException("radius must be positive: " + radius);
        }
    }

    /** A radius-bounded {@code /butcher} over hostile mobs, clamped to {@code maxRadius}. */
    public static PurgeSelection butcher(int radius, int maxRadius) {
        return new PurgeSelection(Scope.RADIUS, clampRadius(radius, maxRadius), Optional.empty(), Category.MONSTERS);
    }

    /** A radius-bounded {@code /remove <type>} over a single named entity type, clamped to {@code maxRadius}. */
    public static PurgeSelection remove(String typeId, int radius, int maxRadius) {
        return new PurgeSelection(
                Scope.RADIUS, clampRadius(radius, maxRadius), normaliseType(typeId), Category.NAMED_TYPE);
    }

    /** A world-wide {@code /killall}: a named type, or every entity when {@code typeId} is blank. */
    public static PurgeSelection killAll(String typeId) {
        Optional<String> type = normaliseType(typeId);
        return new PurgeSelection(Scope.WORLD, 0, type, type.isEmpty() ? Category.ALL_ENTITIES : Category.NAMED_TYPE);
    }

    private static Optional<String> normaliseType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(typeId.trim().toLowerCase(Locale.ROOT));
    }

    private static int clampRadius(int radius, int maxRadius) {
        int ceiling = maxRadius < 1 ? DEFAULT_MAX_RADIUS : maxRadius;
        int floored = Math.max(1, radius);
        return Math.min(floored, ceiling);
    }
}
