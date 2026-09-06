package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code communication_*} placeholders. It is an adapter over the
 * communication context's two readable runtime states wired during bootstrap, the global chat lock the {@code
 * /togglechat} command flips, and the per-player announcer subscription the {@code /broadcasttoggle} command
 * flips. When the communication module is disabled the seam is absent and the placeholders degrade to the dash.
 *
 * <p>{@link #chatEnabled()} is server-wide, the same for every requester, so it answers regardless of who asks.
 * {@link #receivesBroadcasts(PlayerRef)} reads the live per-player subscription, which is only meaningful for an
 * online player (the store resolves the connected player), so the resolver gates it behind the online flag and
 * renders the dash for an offline requester.
 */
public interface CommunicationPlaceholders {

    /** Whether public chat is currently open server-wide: {@code false} while {@code /togglechat} holds it locked. */
    boolean chatEnabled();

    /** Whether {@code who} currently receives the rotating announcer broadcasts (the {@code /broadcasttoggle} state). */
    boolean receivesBroadcasts(PlayerRef who);
}
