package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;

/**
 * The raw icon token an owner picked for their warp, stored verbatim and left opaque to the domain. It may be a
 * material name, a {@code base64:} head texture, an {@code itemsadder:} / {@code oraxen:} / {@code nexo:} / {@code craftengine:} id, an
 * {@code hdb:} head id, or a {@code cmd:} custom-model-data form: the domain neither knows nor cares which. The
 * scheme is resolved to an actual item only in the presentation adapter, so keeping the token uninterpreted here
 * means adding a new icon source later touches the adapter alone, never this value object or the schema.
 *
 * <p>Only shape is guarded, non-blank and no longer than {@value #MAX_LENGTH} characters, matching the icon
 * column width.
 *
 * @param value the opaque icon token, resolved downstream
 */
public record IconSpec(String value) {

    /** Longest accepted icon token; mirrors the persisted column width. */
    public static final int MAX_LENGTH = 256;

    public IconSpec {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("icon spec must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "icon spec must be at most " + MAX_LENGTH + " characters: " + value.length());
        }
    }

    /** Build an icon token from raw input, trimming surrounding whitespace but leaving the scheme untouched. */
    public static IconSpec of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new IconSpec(raw.strip());
    }
}
