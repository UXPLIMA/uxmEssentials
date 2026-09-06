package com.uxplima.uxmessentials.itemworld.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.itemworld.domain.MobSpec;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * A {@code /spawnmob} spawned mobs into a world: a real, observable effect worth bridging to other plugins.
 * Published after the adapter has spawned the entities, carrying who spawned them, the validated request, the
 * world they landed in, the count actually spawned (which may be below {@link MobSpec#amount()} if the region
 * refused some), and when.
 *
 * @param actor the staff member who ran {@code /spawnmob}
 * @param spec the validated spawn request
 * @param world the world the mobs were spawned in
 * @param spawned the number of entities actually spawned
 * @param at the instant the spawn completed
 */
public record MobsSpawned(PlayerRef actor, MobSpec spec, WorldRef world, int spawned, Instant at)
        implements ItemWorldEvent {

    public MobsSpawned {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(at, "at");
        if (spawned < 0) {
            throw new IllegalArgumentException("spawned count must be non-negative: " + spawned);
        }
    }
}
