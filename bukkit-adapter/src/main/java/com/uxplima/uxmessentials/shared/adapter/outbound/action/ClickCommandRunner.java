package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import org.bukkit.entity.Player;

/**
 * The seam between a click-interaction listener and the two ways a bound command can run: as the server console
 * or as the clicking player. Pulling it behind this port keeps the listener's prefix/substitution logic
 * unit-testable against a recording fake, with the Bukkit dispatch isolated in the single implementation. Shared
 * by the feature contexts whose interactable fixtures dispatch commands on a click (npc, holograms, …).
 */
public interface ClickCommandRunner {

    /** Run {@code command} as the server console ({@code Bukkit.dispatchCommand} on the console sender). */
    void runAsConsole(String command);

    /** Run {@code command} as {@code player} ({@code player.performCommand}). */
    void runAsPlayer(Player player, String command);

    /**
     * Run {@code command} as {@code player} with operator permissions for that one dispatch, the implementation
     * temporarily grants op, performs the command, and restores the prior op state (even on failure).
     */
    void runAsPlayerOp(Player player, String command);
}
