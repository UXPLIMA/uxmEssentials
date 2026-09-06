package com.uxplima.uxmessentials.homes.domain;

import java.util.Objects;

/**
 * A player-facing display label for a home. Arbitrary mixed-case text the player typed, stripped
 * of surrounding whitespace and capped at {@link #MAX_LENGTH} characters.
 *
 * <p>Unlike the retired {@code HomeName}, the label is NOT normalised to lowercase; {@code "Base"}
 * and {@code "base"} are different labels. The label is purely cosmetic. It does not participate
 * in home identity, which is carried by {@link HomeSlot}.
 *
 * @param value the stripped label text (non-null, non-blank, at most {@value #MAX_LENGTH} chars)
 */
public record HomeLabel(String value) {

    /** A UX cap on label length, well within the 64-char {@code homes.label} column in the schema. */
    public static final int MAX_LENGTH = 32;

    public HomeLabel {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Builds a label from raw player input. Strips surrounding whitespace; rejects blank or overlong
     * text.
     */
    public static HomeLabel of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("home label must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("home label must be at most " + MAX_LENGTH + " characters");
        }
        return new HomeLabel(trimmed);
    }

    @Override
    public String toString() {
        return value;
    }
}
