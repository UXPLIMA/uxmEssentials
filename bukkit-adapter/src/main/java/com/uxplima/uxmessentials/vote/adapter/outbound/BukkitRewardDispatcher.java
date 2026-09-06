package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit {@link RewardDispatcher}: runs each reward command from the console sender, substituting the
 * {@code {player}} placeholder with the rewarded player's name. Console-command execution touches global
 * game state and the command map, so the whole batch is dispatched on the global region thread through the
 * injected {@link Scheduler} port, never the legacy {@code BukkitScheduler}. An empty batch hops nowhere.
 *
 * <p>The reward lines are operator-authored config content dispatched as written, not player-facing
 * messages, so they do not flow through the message catalog; the only transform is the literal
 * {@code {player}} substitution.
 */
@NullMarked
public final class BukkitRewardDispatcher implements RewardDispatcher {

    private static final String PLAYER_TOKEN = "{player}";

    private final Scheduler scheduler;

    public BukkitRewardDispatcher(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void dispatch(List<String> commands, String playerName) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(playerName, "playerName");
        if (commands.isEmpty()) {
            return;
        }
        scheduler.onGlobal(() -> runAll(commands, playerName));
    }

    private static void runAll(List<String> commands, String playerName) {
        for (String command : commands) {
            String resolved = command.replace(PLAYER_TOKEN, playerName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }
}
