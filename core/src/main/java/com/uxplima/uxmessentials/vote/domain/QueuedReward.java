package com.uxplima.uxmessentials.vote.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A reward batch held for a player who voted while offline: the console commands to run (with the
 * {@code {player}} placeholder still in them) and when the batch was queued. Drained and dispatched
 * when the player next joins, so a vote is never lost to an offline voter, the durable rows behind
 * this batch survive a restart.
 *
 * @param player the offline voter the batch is owed to
 * @param commands the reward console commands, in order, still carrying {@code {player}}
 * @param queuedAt when the batch was enqueued
 */
public record QueuedReward(PlayerRef player, List<String> commands, Instant queuedAt) {

    public QueuedReward {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(queuedAt, "queuedAt");
        commands = List.copyOf(commands);
    }
}
