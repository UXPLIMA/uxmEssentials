package com.uxplima.uxmessentials.playerstate.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player was healed by {@code /heal}. An apply-once effect (health restored, optionally effects cleared)
 * that leaves no persisted flag, so this event is the only record the context emits for it.
 *
 * @param subject the player who was healed
 * @param actor the player who ran the command
 * @param at when the heal happened
 */
public record Healed(PlayerRef subject, PlayerRef actor, Instant at) implements PlayerStateEvent {

    public Healed {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(at, "at");
    }
}
