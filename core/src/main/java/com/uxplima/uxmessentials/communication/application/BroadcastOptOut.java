package com.uxplima.uxmessentials.communication.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.event.BroadcastOptOutToggled;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /broadcasttoggle}: flip whether a player receives announcer broadcasts. It toggles the player's bit in
 * the {@link BroadcastOptOutStore}, confirms the new state with the matching plugin {@link CommunicationMessageKey},
 * and publishes {@link BroadcastOptOutToggled} so an audit line and any observer see the change. The announcer's
 * {@code NextAnnouncement} fan-out reads the same store, so an opted-out player is skipped on the next tick.
 *
 * <p>The opt-out bit is the plugin's own per-player state. The confirmation is a {@code MessageKey}, not operator
 * content. The use case never renders an announcer line itself; it only governs the subscription.
 */
public final class BroadcastOptOut {

    private final BroadcastOptOutStore store;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public BroadcastOptOut(BroadcastOptOutStore store, Notifier notifier, DomainEventPublisher events, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Flip {@code player}'s broadcast subscription and confirm the new state. Returns the new opt-out state:
     * {@code true} when the player is now opted out (no more broadcasts), {@code false} when opted back in.
     */
    public boolean toggle(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        boolean optedOut = store.toggle(player);
        notifier.send(player, confirmation(optedOut));
        events.publish(new BroadcastOptOutToggled(player, optedOut, clock.instant()));
        return optedOut;
    }

    private static CommunicationMessageKey confirmation(boolean optedOut) {
        // Opted out -> broadcasts now OFF for this player; opted back in -> broadcasts ON.
        return optedOut ? CommunicationMessageKey.BROADCAST_TOGGLE_OFF : CommunicationMessageKey.BROADCAST_TOGGLE_ON;
    }
}
