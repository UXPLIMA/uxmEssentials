package com.uxplima.uxmessentials.vote.application.port;

import java.util.Collection;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port giving the vote use cases the set of currently online players, so a vote party can pay
 * its rewards to every connected player and the thank-you broadcast can reach them. Application code
 * never iterates {@code Bukkit.getOnlinePlayers()}. It asks this port; the adapter maps the live
 * players to {@link PlayerRef}s.
 */
public interface VoteAudience {

    /** Every player currently connected, as a snapshot for the calling use case to iterate. */
    Collection<PlayerRef> online();
}
