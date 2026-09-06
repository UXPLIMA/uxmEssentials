package com.uxplima.uxmessentials.itemworld.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * An entity-purge verb ({@code /butcher}, {@code /killall}, {@code /remove}) swept entities from a world, a
 * real, observable effect worth bridging to other plugins. Published after the adapter has removed the
 * entities, carrying who ran it, the validated selection, the world swept, the count actually removed, and
 * when. The use case audit-logs the same action; the event lets a listener react (e.g. a region cache
 * invalidation) without importing this package.
 *
 * @param actor the staff member who ran the purge
 * @param selection the validated purge selection
 * @param world the world that was swept
 * @param removed the number of entities removed
 * @param at the instant the purge completed
 */
public record EntitiesPurged(PlayerRef actor, PurgeSelection selection, WorldRef world, int removed, Instant at)
        implements ItemWorldEvent {

    public EntitiesPurged {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(at, "at");
        if (removed < 0) {
            throw new IllegalArgumentException("removed count must be non-negative: " + removed);
        }
    }
}
