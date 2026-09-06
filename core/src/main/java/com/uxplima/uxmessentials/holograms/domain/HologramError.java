package com.uxplima.uxmessentials.holograms.domain;

import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;

/**
 * The modelled failures a hologram operation can produce. Each value carries the
 * {@link HologramsMessageKey} the command adapter renders, so a use case returns a
 * {@code Result.err(HologramError.X)} and the caller never re-derives the message. The error carries it,
 * and the failure reason and its localized text never drift apart.
 */
public enum HologramError {

    /** A name no hologram exists under: {@code /hologram delete}, {@code addline}, {@code movehere}, … */
    NOT_FOUND(HologramsMessageKey.HOLOGRAM_NOT_FOUND),

    /** {@code /hologram create} for a name a hologram already exists under. */
    NAME_TAKEN(HologramsMessageKey.HOLOGRAM_NAME_TAKEN),

    /** A line index outside the hologram's current line range ({@code setline}, {@code removeline}). */
    LINE_INDEX_OUT_OF_RANGE(HologramsMessageKey.HOLOGRAM_LINE_INDEX_INVALID),

    /** Removing the line would leave the hologram empty: a hologram must keep at least one line. */
    WOULD_BE_EMPTY(HologramsMessageKey.HOLOGRAM_MIN_ONE_LINE),

    /** {@code /hologram page add} on a non-text hologram: only text holograms render paged lines. */
    NOT_TEXT_HOLOGRAM(HologramsMessageKey.HOLOGRAM_PAGE_NOT_TEXT),

    /** {@code /hologram page remove} on a hologram that has no page set (it is an ordinary single-page hologram). */
    NOT_MULTI_PAGE(HologramsMessageKey.HOLOGRAM_PAGE_NOT_MULTIPAGE),

    /** A page index outside the hologram's current page range ({@code /hologram page remove}). */
    PAGE_INDEX_OUT_OF_RANGE(HologramsMessageKey.HOLOGRAM_PAGE_INDEX_INVALID),

    /** An action index outside the hologram's current action chain ({@code /hologram action set|move|remove}). */
    ACTION_INDEX_OUT_OF_RANGE(HologramsMessageKey.HOLOGRAM_ACTION_INDEX_INVALID);

    private final HologramsMessageKey messageKey;

    HologramError(HologramsMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public HologramsMessageKey messageKey() {
        return messageKey;
    }
}
