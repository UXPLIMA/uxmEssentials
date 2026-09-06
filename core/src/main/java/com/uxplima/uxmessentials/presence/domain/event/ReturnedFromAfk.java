package com.uxplima.uxmessentials.presence.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player left the AFK state. Either by running {@code /afk} again or by the first sign of activity (a move,
 * a chat line, a command) clearing the flag. Carries no reason, since the player is now active. The
 * status-placeholder path and other plugins observe this to clear the AFK indicator.
 *
 * @param subject the player who returned from AFK
 * @param at when the transition happened
 */
public record ReturnedFromAfk(PlayerRef subject, Instant at) implements PresenceEvent {

    public ReturnedFromAfk {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(at, "at");
    }
}
