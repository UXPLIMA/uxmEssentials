package com.uxplima.uxmessentials.warps.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * One welcome message a warp shows on arrival: the {@code message} text and the delivery {@code type}. The
 * type is validated and normalised at the record boundary against the {@link #VALID_TYPES known set}
 * upper-cased, and any value outside the set (including a legacy or malformed stored value) coerced to
 * {@code CHAT}, so a delivery switch elsewhere never has to defend against an unexpected type.
 */
public record WelcomeMessage(String message, String type) {

    /** The delivery channels a welcome message may use. */
    public static final Set<String> VALID_TYPES = Set.of("CHAT", "ACTION_BAR", "TITLE", "SUBTITLE", "BOSS_BAR");

    public WelcomeMessage {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(type, "type");
        type = normalizeType(type);
    }

    private static String normalizeType(String type) {
        String upper = type.toUpperCase(Locale.ROOT).trim();
        return VALID_TYPES.contains(upper) ? upper : "CHAT";
    }
}
