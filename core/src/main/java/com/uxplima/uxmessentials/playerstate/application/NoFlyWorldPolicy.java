package com.uxplima.uxmessentials.playerstate.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The pure rule behind no-fly worlds: an operator lists worlds where plugin-granted flight is not allowed
 * ({@code no-fly-worlds = ["pvp", "arena"]}). This policy answers "is {@code world} a no-fly world?", and the
 * adapter uses the answer in two places. Refusing a {@code /fly} that would enable flight in such a world,
 * and stripping plugin flight when a flying player walks into one. In both cases unless the player holds the
 * bypass node.
 *
 * <p>The match on the world name is case-insensitive, matching the per-world command blocker's convention.
 * The domain never reads a live world; the adapter passes in the player's current world name. When the list
 * is empty the adapter skips the check entirely, so the feature is zero-overhead when unconfigured.
 */
public final class NoFlyWorldPolicy {

    private final Set<String> noFlyWorlds;

    /**
     * @param noFlyWorlds the world names where plugin flight is disallowed, in any case; blank entries are
     *     dropped so an empty-string entry never matches every world
     */
    public NoFlyWorldPolicy(Collection<String> noFlyWorlds) {
        Objects.requireNonNull(noFlyWorlds, "noFlyWorlds");
        Set<String> worlds = new HashSet<>();
        for (String world : noFlyWorlds) {
            if (world == null) {
                continue;
            }
            String normalized = world.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                worlds.add(normalized);
            }
        }
        this.noFlyWorlds = Set.copyOf(worlds);
    }

    /** True when flight is not allowed in {@code world}. */
    public boolean isNoFly(String world) {
        Objects.requireNonNull(world, "world");
        return noFlyWorlds.contains(world.toLowerCase(Locale.ROOT));
    }

    /** True when no world is no-fly, so the adapter can skip the check. */
    public boolean isEmpty() {
        return noFlyWorlds.isEmpty();
    }
}
