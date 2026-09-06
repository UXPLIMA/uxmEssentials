package com.uxplima.uxmessentials.homes.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A Bukkit material identifier stored as an uppercase string: for example {@code "GRASS_BLOCK"}.
 *
 * <p>The identifier is kept as a plain {@code String} rather than a Bukkit {@code Material} enum
 * so the domain stays free of platform types. The adapter resolves it to a {@code Material} when
 * rendering the GUI icon.
 *
 * @param materialName upper-cased, stripped material name (non-null, non-blank)
 */
public record HomeIcon(String materialName) {

    public HomeIcon {
        Objects.requireNonNull(materialName, "materialName");
    }

    /**
     * Builds an icon from a raw material name. Strips surrounding whitespace and uppercases using the
     * root locale so the result is locale-independent; rejects blank input.
     */
    public static HomeIcon of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String token = raw.strip().toUpperCase(Locale.ROOT);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("home icon must not be blank");
        }
        return new HomeIcon(token);
    }

    @Override
    public String toString() {
        return materialName;
    }
}
