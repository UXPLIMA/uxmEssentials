package com.uxplima.uxmessentials.vote.domain.reward;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One reward bundle: the effects to apply when this spec is granted, gated by an optional chance, an
 * optional permission, and an optional world filter. The commands run from the console (with the
 * {@code {player}} placeholder), the messages go to the voter, the broadcasts go to everyone, and the
 * items are granted to the voter. All of this is operator config content, not {@code MessageKey} strings.
 *
 * <p>The chance is a percentage in {@code 0..100}: the engine rolls {@code 1..100} and grants the spec
 * when the roll is at most {@code chancePercent} (so {@code 100} always grants, {@code 0} never does).
 * The world filter is inclusive. An empty set means "any world"; a non-empty set means the voter must be
 * in one of the named worlds (which the engine only knows for an online voter).
 *
 * @param chancePercent the grant probability as a percentage in {@code 0..100}
 * @param requiredPermission a permission the voter must hold, or empty for no permission gate
 * @param commands the console commands to run, in order, carrying {@code {player}}
 * @param messages the messages to send the voter, in order
 * @param broadcasts the messages to broadcast to everyone, in order
 * @param items the items to grant the voter, in order
 * @param worlds the worlds this spec applies in; empty means any world
 */
public record RewardSpec(
        int chancePercent,
        Optional<String> requiredPermission,
        List<String> commands,
        List<String> messages,
        List<String> broadcasts,
        List<ItemReward> items,
        Set<String> worlds) {

    public RewardSpec {
        Objects.requireNonNull(requiredPermission, "requiredPermission");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(broadcasts, "broadcasts");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(worlds, "worlds");
        if (chancePercent < 0 || chancePercent > 100) {
            throw new IllegalArgumentException("chancePercent must be in 0..100: " + chancePercent);
        }
        commands = List.copyOf(commands);
        messages = List.copyOf(messages);
        broadcasts = List.copyOf(broadcasts);
        items = List.copyOf(items);
        worlds = Set.copyOf(worlds);
    }

    /** True when this spec's world filter permits {@code worldName}: an empty filter permits any world. */
    public boolean appliesInWorld(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
