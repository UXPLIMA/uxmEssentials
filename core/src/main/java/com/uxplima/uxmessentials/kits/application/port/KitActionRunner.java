package com.uxplima.uxmessentials.kits.application.port;

import java.util.List;

import com.uxplima.uxmessentials.kits.domain.KitAction;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Runs a kit's ordered, typed claim/deny {@link KitAction actions} for a player. The {@code :core} layer asks
 * for a list of actions to be performed. It never decides how a sound is played, a title shown, a firework
 * launched, or a command dispatched, nor on which thread. The adapter executes each action type on the correct
 * Folia scheduler (player-targeted effects on the player's entity thread, broadcasts and console commands on the
 * global thread) and honours a {@code WAIT_TICKS} action by re-scheduling the remaining actions after the stated
 * delay through the Scheduler port, never a {@code BukkitRunnable}.
 *
 * <p>{@link com.uxplima.uxmessentials.kits.application.ClaimKit} owns the ordering: it splits the kit's claim
 * actions into the before-items and after-items halves and grants the items between the two {@code run} calls,
 * and runs the deny actions when a gate refuses the claim. The runner only sequences the actions it is handed,
 * in their declared order. A malformed individual action (a bad sound or title spec, an unknown particle) is
 * logged and skipped inside the adapter so a single typo never throws on the claim path or aborts the rest.
 *
 * <p>The {@code kit} is passed so the adapter can substitute the standard claim tokens ({@code %player%},
 * {@code {player}}, the kit id) in command and text values; it never re-evaluates the kit's gates.
 */
public interface KitActionRunner {

    /**
     * Run {@code actions} for {@code who} in their declared order, substituting {@code kit}'s claim tokens into
     * each action's value. A no-op when {@code actions} is empty. Fire-and-forget: any per-action delay
     * ({@code WAIT_TICKS}) is handled internally by re-scheduling the remainder, so the call returns at once.
     */
    void run(PlayerRef who, KitDefinition kit, List<KitAction> actions);
}
