package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * Parses a custom-colour line typed into the picker's anvil into a packed ARGB int. Accepts a hex value with an
 * optional leading {@code #}, either six digits ({@code RRGGBB}, an opaque {@code 0xFF} alpha is supplied) or
 * eight digits ({@code AARRGGBB}, the alpha is taken as typed). Anything else, a wrong length, a non-hex
 * character, blank input. Returns empty so the property can reject it with a catalog message and re-open the
 * picker rather than writing a garbage colour.
 *
 * <p>Kept free of any module dependency so the shared colour picker has no edge into a feature context; the
 * holograms command surface carries its own richer parser (named colours, channel triples) for the CLI, but the
 * GUI's custom field is deliberately hex-only.
 */
@NullMarked
public final class ColourHex {

    private static final int OPAQUE_ALPHA = 0xFF;
    private static final int RRGGBB = 6;
    private static final int AARRGGBB = 8;
    private static final int HEX_RADIX = 16;

    private ColourHex() {}

    /** Parse {@code raw} as {@code #RRGGBB} / {@code #AARRGGBB} (the {@code #} optional) into a packed ARGB int. */
    public static Optional<Integer> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String hex = raw.strip();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != RRGGBB && hex.length() != AARRGGBB) {
            return Optional.empty();
        }
        try {
            long bits = Long.parseLong(hex, HEX_RADIX);
            int argb = hex.length() == RRGGBB ? (OPAQUE_ALPHA << 24 | (int) bits) : (int) bits;
            return Optional.of(argb);
        } catch (NumberFormatException notHex) {
            return Optional.empty();
        }
    }
}
