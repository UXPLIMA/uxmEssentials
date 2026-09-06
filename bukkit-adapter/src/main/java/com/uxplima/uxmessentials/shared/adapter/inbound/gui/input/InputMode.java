package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * Which backend captures a line of text for an input point: a vanilla anvil prompt, the next chat message, a
 * transient sign, or a native dialog screen. The choice is operator config, resolved per input-point key (with a
 * global default) by {@link InputSettings}; the call site is mode-agnostic. It hands a request to {@link TextInput}
 * and gets the typed line back whichever backend ran.
 */
@NullMarked
public enum InputMode {
    ANVIL,
    CHAT,
    SIGN,
    DIALOG;

    /**
     * Parse an operator-authored mode token ({@code anvil}/{@code chat}/{@code sign}/{@code dialog}, any case),
     * returning empty for an unknown or blank token so the caller can fall back to a default rather than fail the
     * whole config load.
     */
    public static Optional<InputMode> parse(String token) {
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "anvil" -> Optional.of(ANVIL);
            case "chat" -> Optional.of(CHAT);
            case "sign" -> Optional.of(SIGN);
            case "dialog" -> Optional.of(DIALOG);
            default -> Optional.empty();
        };
    }
}
