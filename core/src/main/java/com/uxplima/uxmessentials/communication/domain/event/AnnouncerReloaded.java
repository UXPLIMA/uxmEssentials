package com.uxplima.uxmessentials.communication.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * The operator reloaded the announcer (a {@code /announce reload}, a {@code /uxmess reload communication}, or the
 * module's own reload path swapped a fresh {@code AnnouncerConfig} in). The {@code lineCount} records how many
 * announcements the new config carries, for an audit line and for the reload confirmation. It is a count, not the
 * operator's template text. The running announcer timer reads the swapped config on its next tick.
 *
 * @param lineCount the number of announcements in the reloaded config
 * @param at when the reload happened
 */
public record AnnouncerReloaded(int lineCount, Instant at) implements CommunicationEvent {

    public AnnouncerReloaded {
        Objects.requireNonNull(at, "at");
        if (lineCount < 0) {
            throw new IllegalArgumentException("lineCount must not be negative: " + lineCount);
        }
    }
}
