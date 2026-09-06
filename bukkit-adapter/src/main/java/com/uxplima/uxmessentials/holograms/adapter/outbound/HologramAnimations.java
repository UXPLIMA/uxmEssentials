package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Locale;

import org.jspecify.annotations.NullMarked;

/**
 * Expands a hologram line's optional inline animation directive into the MiniMessage source rendered for the
 * current frame. A line whose source starts with {@code <anim:TYPE>} animates as the hologram re-renders on its
 * refresh interval: each re-render passes a higher {@code phase}, so the rendered text advances a frame. A line
 * with no directive is returned unchanged, so the whole mechanism costs nothing for a normal line and needs no
 * persistence or command of its own (the directive is part of the line text the operator already sets).
 *
 * <p>Built-in types (the text after the directive is the placeholder-resolved line):
 *
 * <ul>
 *   <li>{@code rainbow}: wraps the text in MiniMessage's {@code <rainbow:phase>}, so the hue cycles each frame.
 *   <li>{@code typewriter}: reveals one more character per frame, holds the full text briefly, then restarts.
 *   <li>{@code scroll}, marquees the text horizontally, one character per frame, with a short trailing gap.
 * </ul>
 *
 * <p>{@code typewriter} and {@code scroll} cut the text by character, so they expect plain text (a MiniMessage
 * tag could be split mid-token); {@code rainbow} wraps the whole text and is safe with any content. An unknown
 * type leaves the line unchanged, so a typo renders the literal directive rather than throwing.
 */
@NullMarked
final class HologramAnimations {

    private static final String PREFIX = "<anim:";
    /** Frames the typewriter holds the fully-revealed text before it restarts. */
    private static final int TYPEWRITER_HOLD = 6;
    /** The trailing gap (spaces) between the end and the start of a scrolled marquee. */
    private static final String SCROLL_GAP = "   ";

    private HologramAnimations() {}

    /** Expand {@code source}'s leading {@code <anim:TYPE>} directive for {@code phase}, or return it unchanged. */
    static String expand(String source, int phase) {
        if (!source.startsWith(PREFIX)) {
            return source;
        }
        int close = source.indexOf('>');
        if (close < 0) {
            return source;
        }
        String type = source.substring(PREFIX.length(), close).strip().toLowerCase(Locale.ROOT);
        String text = source.substring(close + 1);
        int frame = Math.max(0, phase);
        return switch (type) {
            case "rainbow" -> "<rainbow:" + frame + ">" + text;
            case "typewriter" -> typewriter(text, frame);
            case "scroll" -> scroll(text, frame);
            default -> source;
        };
    }

    /**
     * Strip a leading {@code <anim:TYPE>} directive, returning just the text. Used on the per-viewer placeholder
     * path, which renders a static frame: animation and per-viewer placeholders do not combine, so the directive
     * is removed rather than shown literally or left to the frame logic.
     */
    static String stripDirective(String source) {
        if (!source.startsWith(PREFIX)) {
            return source;
        }
        int close = source.indexOf('>');
        return close < 0 ? source : source.substring(close + 1);
    }

    private static String typewriter(String text, int phase) {
        if (text.isEmpty()) {
            return text;
        }
        int period = text.length() + TYPEWRITER_HOLD;
        int revealed = Math.min(phase % period, text.length());
        return text.substring(0, revealed);
    }

    private static String scroll(String text, int phase) {
        if (text.isEmpty()) {
            return text;
        }
        String marquee = text + SCROLL_GAP;
        int offset = phase % marquee.length();
        return marquee.substring(offset) + marquee.substring(0, offset);
    }
}
