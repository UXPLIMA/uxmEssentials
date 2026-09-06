package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;

/**
 * The owner's free-text blurb for a warp: the sentence or two that sells it in a browse menu. An absent
 * description is modelled by an {@code Optional<WarpDescription>} on the aggregate, never by an empty string, so
 * this value object treats a blank blurb as invalid: if there is nothing to say, there is no description at all.
 * That keeps "no description" a single, unambiguous state rather than the two-way ambiguity of {@code null} versus
 * {@code ""}.
 *
 * <p>Only shape is enforced here, non-blank and no longer than {@value #MAX_LENGTH} characters, matching the
 * description column width. Markup is left to the presentation adapter.
 *
 * @param value the trimmed description text
 */
public record WarpDescription(String value) {

    /** Longest accepted description; mirrors the persisted column width. */
    public static final int MAX_LENGTH = 512;

    public WarpDescription {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("warp description must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "warp description must be at most " + MAX_LENGTH + " characters: " + value.length());
        }
    }

    /** Build a description from raw player input, trimming surrounding whitespace. */
    public static WarpDescription of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new WarpDescription(raw.strip());
    }
}
