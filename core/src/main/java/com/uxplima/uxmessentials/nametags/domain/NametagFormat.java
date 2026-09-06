package com.uxplima.uxmessentials.nametags.domain;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * One named nametag format: the multi-line text to float above the wearer's head, the {@link DisplayCondition}
 * deciding which wearers it applies to, a {@code priority} that breaks the field of matching formats, the
 * {@link NametagAppearance} of the text display, and the {@link NametagVisibility} rules. A server may author
 * several formats (a staff format, a vip format, a default) and {@link NametagConfig#select} picks the
 * highest-priority one whose condition matches each wearer.
 *
 * <p>This mirrors the tablist's {@code TablistFormat}: the {@code condition} gates which format a wearer gets and
 * {@code priority} ranks the matches, while {@code lines}/{@code appearance}/{@code visibility} are the
 * nametag-specific payload. The {@code lines} are raw operator content rendered per viewer through the placeholder
 * pipeline, MiniMessage, and the animation catalog by the adapter, then newline-joined into the single
 * {@code TextDisplay}. An empty {@code lines} list is allowed and means "no nametag", a deliberate way to author
 * a format that suppresses the nametag for a matching group.
 *
 * @param name the format name, non-blank (the config map key; used for operator-facing identification and tie-break)
 * @param condition the per-wearer gate; {@link DisplayCondition#always()} for an unconditional format
 * @param priority the selection rank; higher wins, ties broken by name (see {@link NametagConfig#select})
 * @param lines the operator-authored lines, defensively copied (empty → no nametag)
 * @param appearance the text-display appearance settings
 * @param visibility the per-wearer/per-viewer visibility rules
 */
public record NametagFormat(
        String name,
        DisplayCondition condition,
        int priority,
        List<String> lines,
        NametagAppearance appearance,
        NametagVisibility visibility) {

    public NametagFormat {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(visibility, "visibility");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a nametag format name must not be blank");
        }
        lines = List.copyOf(lines);
    }
}
