package com.uxplima.uxmessentials.moderation.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A jail location was defined or re-anchored. {@code /setjail <name>} saved the staff member's current
 * position under {@code jail}. A {@code /setjail} onto an existing stored name overwrites that jail's location
 * and raises this same event; the store is keyed by name, so a redefinition is an upsert, not a separate kind.
 *
 * @param jail the canonical lowercase jail name the location was saved under
 * @param definedBy the staff member who ran the command
 * @param at when the location was defined
 */
public record JailLocationDefined(String jail, PlayerRef definedBy, Instant at) implements ModerationEvent {

    public JailLocationDefined {
        Objects.requireNonNull(jail, "jail");
        Objects.requireNonNull(definedBy, "definedBy");
        Objects.requireNonNull(at, "at");
    }
}
