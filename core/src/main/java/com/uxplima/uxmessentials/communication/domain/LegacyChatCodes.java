package com.uxplima.uxmessentials.communication.domain;

import java.util.Objects;

/**
 * The pure rule that decides whether a prefix/suffix string is written with legacy colour codes (a {@code &c} or
 * {@code §c} sequence) rather than MiniMessage. A permission plugin may report a group's prefix in either dialect,
 * and the two must be parsed differently: MiniMessage through the normal parser, legacy through Adventure's legacy
 * serializer. This helper answers only "which dialect is this?" so the adapter can pick the right parser without
 * double-parsing. It holds no Adventure type and never renders anything, keeping the decision unit-testable
 * without Bukkit.
 *
 * <p>A string counts as legacy when it contains a colour/format marker, {@code &} (ampersand) or the section sign
 *. Immediately followed by a recognised code character ({@code 0-9}, {@code a-f}, {@code k-o}, {@code r}, or the
 * {@code x} hex marker, case-insensitively). A lone {@code &} that introduces no code (as in {@code "Tom & Jerry"})
 * is not a legacy string, so a plain ampersand in a prefix does not force the legacy parser.
 */
public final class LegacyChatCodes {

    /** The section sign that begins a legacy code in the {@code §c} dialect; kept as an escape to avoid a raw glyph. */
    private static final char SECTION = '\u00A7';

    private static final char AMPERSAND = '&';

    private LegacyChatCodes() {}

    /** Whether {@code text} carries at least one legacy colour/format code and should be parsed as legacy, not MiniMessage. */
    public static boolean containsLegacyCodes(String text) {
        Objects.requireNonNull(text, "text");
        for (int i = 0; i < text.length() - 1; i++) {
            char marker = text.charAt(i);
            if ((marker == AMPERSAND || marker == SECTION) && isCodeChar(text.charAt(i + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCodeChar(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o')
                || lower == 'r'
                || lower == 'x';
    }
}
