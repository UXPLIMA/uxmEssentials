package com.uxplima.uxmessentials.ranks.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Runs a rank's configured rankup {@code actions} for a player once the advance has been recorded. The
 * {@code :core} layer hands over the rank's raw action lines exactly as authored in {@code ranks.conf}
 * ({@code "console lp user %player% parent set citizen"}, {@code "message <green>Welcome!"}): it never decides
 * how a command is dispatched, a message shown, or a sound played, nor on which thread. The adapter parses each
 * line into the shared click-action model and runs the chain through the same click-action engine the npc and
 * hologram contexts use, giving the operator's "command actions" full reach: set a permission group, run any
 * command, broadcast, play a sound, and so on.
 *
 * <p>The rankup use case owns the ordering. It calls this only on a successful advance, after the pointer has
 * been saved, so a rank's actions never fire for a refused or unaffordable rankup. A malformed individual action
 * line is logged and skipped inside the adapter so a single typo never throws on the rankup path or aborts the
 * rest of the chain.
 */
public interface RankActionRunner {

    /**
     * Run {@code actionLines} for {@code who} in their declared order. A no-op when the list is empty or the
     * player is offline. Fire-and-forget: any per-action delay is handled internally by the adapter's engine, so
     * the call returns at once.
     */
    void run(PlayerRef who, List<String> actionLines);
}
