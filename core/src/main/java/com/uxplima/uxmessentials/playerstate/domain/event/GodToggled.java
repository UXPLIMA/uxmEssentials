package com.uxplima.uxmessentials.playerstate.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's god (damage-immunity) flag was flipped by {@code /god}. The {@code actor} is whoever ran the
 * command and the {@code subject} is the player whose flag changed. They are the same for a self-toggle and
 * differ for a staff toggle via the {@code .others} target.
 *
 * @param subject the player whose god flag changed
 * @param actor the player (or console-as-player) who ran the command
 * @param enabled the new flag value
 * @param at when the toggle happened
 */
public record GodToggled(PlayerRef subject, PlayerRef actor, boolean enabled, Instant at) implements PlayerStateEvent {

    public GodToggled {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(at, "at");
    }
}
