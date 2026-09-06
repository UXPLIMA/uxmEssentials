package com.uxplima.uxmessentials.playerstate.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's hunger was restored by {@code /feed}. An apply-once effect with no persisted flag, so this
 * event is the only record the context emits for it.
 *
 * @param subject the player who was fed
 * @param actor the player who ran the command
 * @param at when the feed happened
 */
public record Fed(PlayerRef subject, PlayerRef actor, Instant at) implements PlayerStateEvent {

    public Fed {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(at, "at");
    }
}
