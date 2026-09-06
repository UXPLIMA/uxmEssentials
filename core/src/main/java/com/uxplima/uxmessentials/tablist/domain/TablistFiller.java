package com.uxplima.uxmessentials.tablist.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One fixed-slot filler entry in a {@link TablistLayout}: a synthetic tab-list row backed by no real player, occupying a
 * given 1-based {@code slot} in the grid, showing operator-authored {@code text}, and optionally carrying a custom
 * {@code skin}. Real players sort into the early slots; fillers fill the rest, positioned by slot through the layout's
 * {@link TablistLayout.Direction}. This is pure operator intent. The {@code text} is raw MiniMessage source the adapter
 * renders per viewer (built-in {@code {tokens}} → PlaceholderAPI → MiniMessage), the same pipeline the header/footer use.
 *
 * <p>The {@code slot} is 1-based: slot {@code 1} is the first tab cell, and the layout's column/row direction maps a slot
 * to the actual grid cell. A non-positive slot is rejected; the codec drops out-of-range slots before constructing one.
 *
 * @param slot the 1-based grid slot this filler occupies, strictly positive
 * @param text the raw MiniMessage source shown for the row (may embed built-in tokens and PlaceholderAPI placeholders)
 * @param skin the custom tab-row skin source, or empty to use the synthetic entry's default (no skin) texture
 */
public record TablistFiller(int slot, String text, Optional<TablistSkinSource> skin) {

    public TablistFiller {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(skin, "skin");
        if (slot <= 0) {
            throw new IllegalArgumentException("a tablist filler slot must be strictly positive, got " + slot);
        }
    }
}
