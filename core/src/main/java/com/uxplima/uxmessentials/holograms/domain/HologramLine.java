package com.uxplima.uxmessentials.holograms.domain;

import java.util.Objects;

/**
 * One line of a hologram's text. The {@link #value} is the raw, unrendered source, a {@code TextDisplay}
 * renders MiniMessage, so the line is stored verbatim and deserialised at the adapter boundary, never in the
 * domain. A blank line carries no visible text and would render an empty row, so it is rejected at
 * construction; a hologram that wants a spacer uses a non-blank placeholder.
 *
 * @param value the raw line source (MiniMessage), as the operator typed it
 */
public record HologramLine(String value) {

    /** Mirrors the {@code hologram_lines.text} column width in the V13 migration. */
    public static final int MAX_LENGTH = 512;

    public HologramLine {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("hologram line must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("hologram line must be at most " + MAX_LENGTH + " characters");
        }
    }

    /** Build a line from raw player input, rejecting blank or overlong text. */
    public static HologramLine of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new HologramLine(raw.strip());
    }

    @Override
    public String toString() {
        return value;
    }
}
