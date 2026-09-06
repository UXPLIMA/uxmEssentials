package com.uxplima.uxmessentials.vote.domain.reward;

import java.util.List;
import java.util.Objects;

/**
 * The resolved effects of one granted {@link RewardSpec}. What the adapter must actually apply for a
 * single passing spec: the console commands to run, the messages to send the voter, the broadcasts to
 * send everyone, and the items to grant. A grant carries no chance/permission/world gate: those have
 * already been evaluated by the engine, and a grant exists only because the spec passed every gate.
 *
 * <p>For an offline voter the adapter queues this grant's {@link #commands()} (the existing
 * {@code QueuedReward} mechanism) and skips the live messages/broadcasts/items.
 *
 * @param commands the console commands to run, in order, carrying {@code {player}}
 * @param messages the messages to send the voter, in order
 * @param broadcasts the messages to broadcast to everyone, in order
 * @param items the items to grant the voter, in order
 */
public record RewardGrant(
        List<String> commands, List<String> messages, List<String> broadcasts, List<ItemReward> items) {

    public RewardGrant {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(broadcasts, "broadcasts");
        Objects.requireNonNull(items, "items");
        commands = List.copyOf(commands);
        messages = List.copyOf(messages);
        broadcasts = List.copyOf(broadcasts);
        items = List.copyOf(items);
    }

    /** The resolved effects of {@code spec}: its commands, messages, broadcasts, and item grants. */
    public static RewardGrant of(RewardSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return new RewardGrant(spec.commands(), spec.messages(), spec.broadcasts(), spec.items());
    }
}
