package com.uxplima.uxmessentials.holograms.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * How a hologram's display faces the viewer, the domain mirror of the native {@code Display.Billboard} so the
 * domain names the four facing modes without importing Bukkit. {@link #CENTER} (always turns to face the
 * viewer) is the default; {@link #FIXED} never turns; {@link #VERTICAL} and {@link #HORIZONTAL} turn about one
 * axis only. The adapter maps each value to the native enum at the rendering boundary.
 */
public enum Billboard {

    /** Always faces the viewer (pivots on both axes), the default. */
    CENTER,

    /** Never turns; renders at its placed orientation. */
    FIXED,

    /** Turns about the vertical axis only. */
    VERTICAL,

    /** Turns about the horizontal axis only. */
    HORIZONTAL;

    /** Parse operator input case-insensitively, returning empty for an unknown name. */
    public static Optional<Billboard> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.strip().toUpperCase(Locale.ROOT);
        for (Billboard value : values()) {
            if (value.name().equals(normalised)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
