package com.uxplima.uxmessentials.holograms.domain;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * What a hologram renders as: lines of {@link HologramType#TEXT} (the default), a single floating item
 * ({@link HologramType#ITEM}), or a single floating block model ({@link HologramType#BLOCK}). The type decides
 * which content a {@link Hologram} carries. A TEXT hologram its {@code lines}, an ITEM hologram its
 * {@code itemMaterial}, a BLOCK hologram its {@code blockData}, and which native Display the adapter spawns.
 *
 * <p>A TEXT hologram must keep at least one line; an ITEM or BLOCK hologram needs none (its content is the
 * model), so the {@link Hologram} line invariant is relaxed for the non-text types. The enum is pure (no
 * Bukkit): the item is a {@link org.bukkit.Material} <em>name</em> and the block a BlockData <em>string</em>,
 * resolved to live types only at the adapter boundary.
 */
public enum HologramType {

    /** Lines of text rendered top-down by a {@code TextDisplay}; the default. */
    TEXT,

    /** A single floating item shown by an {@code ItemDisplay}, its content a {@code Material} name. */
    ITEM,

    /** A single floating block model shown by a {@code BlockDisplay}, its content a BlockData string. */
    BLOCK,

    /** A single floating player head shown by an {@code ItemDisplay}, its content a base64 skin texture. */
    HEAD,

    /** A single frozen, decorative mob shown as a real (AI-less, weightless) entity, its content an entity-type name. */
    ENTITY;

    /** Whether a hologram of this type must carry at least one text line (only {@link #TEXT} does). */
    public boolean requiresLines() {
        return this == TEXT;
    }

    /** Parse a stored or typed value case-insensitively, or empty for {@code null}/blank/unknown. */
    public static Optional<HologramType> parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(HologramType.valueOf(raw.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
