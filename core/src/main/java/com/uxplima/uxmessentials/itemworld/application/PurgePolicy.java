package com.uxplima.uxmessentials.itemworld.application;

import java.util.Objects;

import com.uxplima.uxmessentials.itemworld.domain.ItemWorldError;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The entity-purge selection policy for {@code /butcher [radius]}, {@code /killall [type]} and
 * {@code /remove <type> [radius]}: build a validated {@link PurgeSelection}, clamping the radius to the
 * configured ceiling, before the adapter sweeps. A purge is the most abusable itemworld verb (it can clear a
 * region or a whole world), so the selection, including the radius clamp that stops a million-block sweep,
 * is a domain rule the use case audit-logs.
 *
 * <p>The radius ceiling comes from {@link ItemworldConfig#purgeMaxRadius()}. {@code /remove} requires a type
 * (a blank one is rejected with {@link ItemWorldError#UNKNOWN_MOB}); {@code /butcher} sweeps hostile mobs
 * within a radius; {@code /killall} sweeps a named type world-wide, or every entity when no type is given.
 */
public final class PurgePolicy {

    private final ItemworldConfig config;

    public PurgePolicy(ItemworldConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** {@code /butcher [radius]}: hostile mobs within a radius, clamped to the purge-max-radius. */
    public PurgeSelection butcher(int radius) {
        return PurgeSelection.butcher(radius, config.purgeMaxRadius());
    }

    /** {@code /killall [type]}: a named type world-wide, or every entity when {@code type} is blank. */
    public PurgeSelection killAll(String type) {
        return PurgeSelection.killAll(type);
    }

    /** {@code /remove <type> [radius]}: a named type within a radius; a blank type is rejected. */
    public Result<PurgeSelection, ItemWorldError> remove(String type, int radius) {
        if (type == null || type.isBlank()) {
            return Result.err(ItemWorldError.UNKNOWN_MOB);
        }
        return Result.ok(PurgeSelection.remove(type, radius, config.purgeMaxRadius()));
    }
}
