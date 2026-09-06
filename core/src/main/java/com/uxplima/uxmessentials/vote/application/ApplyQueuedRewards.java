package com.uxplima.uxmessentials.vote.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;

/**
 * Pays out the rewards a player accrued while offline. Run on the player's join: it drains every pending
 * reward batch for them (a transactional select-then-delete in the repository, so each batch pays out
 * exactly once) and dispatches each batch's commands for the now-online player. A player with nothing
 * queued is a cheap no-op: the {@link VoteRepository#hasPending} probe short-circuits before the drain.
 *
 * <p>{@link #applyFor} returns the total number of reward <em>commands</em> applied, not the number of
 * batches: the production repository collapses a player's queued rows into a single batch on drain, so a
 * batch count would always read 0 or 1. With one command queued per vote, the common case, the command
 * total equals the number of votes the returning player is being paid for.
 */
public final class ApplyQueuedRewards {

    private final VoteRepository repository;
    private final RewardDispatcher dispatcher;

    public ApplyQueuedRewards(VoteRepository repository, RewardDispatcher dispatcher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /** Drain and dispatch every reward batch queued for {@code player}; returns how many commands paid out. */
    public int applyFor(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        if (!repository.hasPending(player)) {
            return 0;
        }
        List<QueuedReward> pending = repository.drainFor(player);
        int total = 0;
        for (QueuedReward reward : pending) {
            dispatcher.dispatch(reward.commands(), player.name());
            total += reward.commands().size();
        }
        return total;
    }
}
