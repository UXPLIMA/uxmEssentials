package com.uxplima.uxmessentials.moderation.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A stored jail location was removed: {@code /jail del <name>} deleted the DB-backed jail {@code jail}. Only a
 * stored jail can be removed this way; a config-defined jail name is not in the store, so a {@code /jail del} of
 * such a name is a not-found notice and raises nothing here.
 *
 * @param jail the canonical lowercase jail name that was removed
 * @param removedBy the staff member who ran the command
 * @param at when the location was removed
 */
public record JailLocationRemoved(String jail, PlayerRef removedBy, Instant at) implements ModerationEvent {

    public JailLocationRemoved {
        Objects.requireNonNull(jail, "jail");
        Objects.requireNonNull(removedBy, "removedBy");
        Objects.requireNonNull(at, "at");
    }
}
