package com.uxplima.uxmessentials.playerstate.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's flight allowance was flipped by {@code /fly}. v1 ships the plain on/off toggle only, timed fly
 * is deferred post-v1, so this event carries no remaining-seconds payload.
 *
 * @param subject the player whose fly allowance changed
 * @param actor the player who ran the command
 * @param enabled the new flight allowance
 * @param at when the toggle happened
 */
public record FlyToggled(PlayerRef subject, PlayerRef actor, boolean enabled, Instant at) implements PlayerStateEvent {

    public FlyToggled {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(at, "at");
    }
}
