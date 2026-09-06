package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * How a menu item's spec lore combines with the lore the resolved base icon already carries. A plain material has
 * no lore of its own, so the mode only matters when the base is a serialized stack ({@code b64:…}) or a provider
 * icon that ships its own lore.
 *
 * <ul>
 *   <li>{@link #REPLACE}: the spec lore is the whole lore; the base's own lore is dropped. This is the historic
 *       behaviour and the default, so every existing menu renders unchanged.
 *   <li>{@link #APPEND}: the base's lore first, then the spec lore beneath it.
 *   <li>{@link #PREPEND}: the spec lore first, then the base's own lore beneath it.
 * </ul>
 *
 * <p>The enum is part of the pure, Bukkit-free spec model; the renderer reads {@link MenuItemSpec#loreMode()} to
 * decide how to merge the two lists at draw time.
 */
public enum LoreMode {
    REPLACE,
    APPEND,
    PREPEND;

    /**
     * The mode a {@code lore-mode} spec token names, case-insensitively. An absent, blank, or unrecognised token
     * is {@link #REPLACE}. The safe default that keeps a menu that never declared a mode rendering exactly as it
     * did before, the same fail-soft stance the rest of the spec grammar takes.
     */
    public static LoreMode fromToken(@Nullable String raw) {
        if (raw == null) {
            return REPLACE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "append" -> APPEND;
            case "prepend" -> PREPEND;
            default -> REPLACE;
        };
    }
}
