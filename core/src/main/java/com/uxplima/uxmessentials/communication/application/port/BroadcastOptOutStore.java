package com.uxplima.uxmessentials.communication.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over the set of players who have opted out of announcer broadcasts via {@code /broadcasttoggle}.
 * The default is opted-in: a player not present in the store receives announcements. The adapter backs this with
 * a {@code ConcurrentHashMap}-keyed set (mutated through {@code compute}-style operations) or per-player PDC, so a
 * read on the announcer's fan-out thread and a write on the toggling player's region thread never race.
 *
 * <p>The store holds only the subscription bit (no template content, no schedule) so it is the plugin's own
 * transient state, not operator data. {@link #toggle} flips the bit and returns the new state in one atomic step,
 * which the {@code BroadcastOptOut} use case reports back to the player.
 */
public interface BroadcastOptOutStore {

    /** Whether {@code who} currently receives announcer broadcasts (the default for an unknown player). */
    boolean receivesBroadcasts(PlayerRef who);

    /**
     * Flip {@code who}'s subscription atomically and return the new opt-out state: {@code true} when the player is
     * now opted out (will no longer receive broadcasts), {@code false} when they are opted back in.
     */
    boolean toggle(PlayerRef who);

    /** Drop {@code who}'s entry; called on quit so a disconnected player holds no transient subscription state. */
    void forget(PlayerRef who);
}
