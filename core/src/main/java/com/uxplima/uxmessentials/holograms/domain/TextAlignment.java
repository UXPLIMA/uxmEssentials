package com.uxplima.uxmessentials.holograms.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * How a TEXT hologram's lines align within their panel, the domain mirror of the native
 * {@code TextDisplay.TextAlignment} so the domain names the three modes without importing Bukkit.
 * {@link #CENTER} (lines centred) is the default; {@link #LEFT} and {@link #RIGHT} align to that edge. Only a
 * TEXT hologram has alignment, an item or block display has no text, so the adapter maps this onto the native
 * enum at the rendering boundary and ignores it for the non-text types.
 */
public enum TextAlignment {

    /** Lines centred within the panel, the default. */
    CENTER,

    /** Lines aligned to the left edge. */
    LEFT,

    /** Lines aligned to the right edge. */
    RIGHT;

    /** Parse operator input case-insensitively, returning empty for an unknown name. */
    public static Optional<TextAlignment> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.strip().toUpperCase(Locale.ROOT);
        for (TextAlignment value : values()) {
            if (value.name().equals(normalised)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
