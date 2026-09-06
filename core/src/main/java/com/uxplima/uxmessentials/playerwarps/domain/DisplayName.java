package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;

/**
 * The human-facing label a warp shows in menus and chat, kept distinct from the lowercase lookup id
 * {@link PlayerWarpName}. Unlike the id, this preserves the owner's original casing and spaces, {@code "My Cozy
 * Base"} is what a browser sees, while {@code my_cozy_base} (or whatever id the owner chose) is what commands
 * address. Keeping the two apart lets the id stay strict and collision-safe without forcing players to type in
 * lowercase-slug form.
 *
 * <p>The charset is deliberately unrestricted: colour and MiniMessage markup are the presentation adapter's
 * concern, not this value object's. Here we guard only the shape, non-blank and no longer than
 * {@value #MAX_LENGTH} characters, which mirrors the display-name column width the schema round-trips.
 *
 * @param value the trimmed, case-preserving display label
 */
public record DisplayName(String value) {

    /** Longest accepted display name; mirrors the persisted column width. */
    public static final int MAX_LENGTH = 128;

    public DisplayName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("display name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "display name must be at most " + MAX_LENGTH + " characters: " + value.length());
        }
    }

    /** Build a display name from raw player input, trimming surrounding whitespace but keeping case and spaces. */
    public static DisplayName of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new DisplayName(raw.strip());
    }

    @Override
    public String toString() {
        return value;
    }
}
