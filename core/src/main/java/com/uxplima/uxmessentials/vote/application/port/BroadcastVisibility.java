package com.uxplima.uxmessentials.vote.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over the set of players who have hidden vote broadcasts for themselves. The default is to
 * receive: a player not present in the store still sees the thank-you and party broadcasts. The adapter
 * backs this with a {@code ConcurrentHashMap}-keyed set (mutated through {@code compute}-style operations)
 * or per-player PDC, so a read on the broadcaster's fan-out thread and a write on the toggling player's
 * region thread never race. Mirrors {@code BroadcastOptOutStore} from the communication context.
 *
 * <p>The store holds only the visibility bit, no message content, so it is the plugin's own transient
 * state, not operator data. {@link #toggle} flips the bit and returns the new visibility state in one
 * atomic step for the toggle command to report back to the player.
 */
public interface BroadcastVisibility {

    /** Whether {@code who} currently receives vote broadcasts (the default for an unknown player). */
    boolean receivesBroadcasts(PlayerRef who);

    /**
     * Flip {@code who}'s visibility atomically and return the new state: {@code true} when the player now
     * receives broadcasts again, {@code false} when they have just hidden them.
     */
    boolean toggle(PlayerRef who);
}
