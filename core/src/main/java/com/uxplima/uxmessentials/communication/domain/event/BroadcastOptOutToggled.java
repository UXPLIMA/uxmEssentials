package com.uxplima.uxmessentials.communication.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player toggled whether they receive announcer broadcasts via {@code /broadcasttoggle}. The {@code optedOut}
 * flag is the new state after the toggle: {@code true} means the player will no longer receive scheduled
 * announcements, {@code false} means they opted back in. Other plugins and an audit line observe this; the
 * announcer's {@code NextAnnouncement} fan-out reads the same opt-out set the toggle wrote.
 *
 * @param subject the player who toggled their broadcast subscription
 * @param optedOut the new state, true when the player now opts out of announcements
 * @param at when the toggle happened
 */
public record BroadcastOptOutToggled(PlayerRef subject, boolean optedOut, Instant at) implements CommunicationEvent {

    public BroadcastOptOutToggled {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(at, "at");
    }
}
