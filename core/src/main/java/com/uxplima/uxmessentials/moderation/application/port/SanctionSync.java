package com.uxplima.uxmessentials.moderation.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the cross-server <em>live</em> effect of a sanction. A ban or mute is already enforced
 * durably across the cluster for free. The shared database holds the row and every backend re-reads it on the
 * next login, so this port adds only the live reaction: a peer that already has the sanctioned player
 * connected reacts immediately rather than waiting for the player's next reconnect. The use case calls it
 * after the durable write commits, on the success path only.
 *
 * <p>Kept bus-agnostic so {@code :core} never imports the bus adapter: the adapter binds it to the bus
 * publisher (it builds the {@link com.uxplima.uxmessentials.shared.network.BanChanged} /
 * {@link com.uxplima.uxmessentials.shared.network.MuteChanged} frame and routes it), and a backend with no bus
 * runs against {@link #NONE}, so the single-server path is unchanged: the durable enforcement needs no bus.
 */
public interface SanctionSync {

    /** A no-op sync: announces nothing. The default a use case runs against when the bus is off. */
    SanctionSync NONE = new SanctionSync() {
        @Override
        public void banChanged(PlayerRef target) {
            // No bus: there are no peers to wake, and the durable ban already enforces on every backend's login.
        }

        @Override
        public void muteChanged(PlayerRef target) {
            // No bus: a peer re-reads the mute from the shared DB on the next chat attempt regardless.
        }
    };

    /** Announce that {@code target}'s ban changed so a peer with them online kicks them live. */
    void banChanged(PlayerRef target);

    /** Announce that {@code target}'s mute changed so a peer that caches mute state may refresh it. */
    void muteChanged(PlayerRef target);
}
