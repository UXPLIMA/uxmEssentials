package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The two ways a {@link RuleSet} reads its command lists.
 *
 * <ul>
 *   <li>{@link #WHITELIST}: only the listed commands are allowed; everything else is denied. A useful lock-down
 *       mode where an operator names the short list of commands a group may run.
 *   <li>{@link #BLACKLIST}: the listed commands are denied; everything else is allowed. The forgiving mode where an
 *       operator names only the handful of commands to hide.
 * </ul>
 *
 * <p>The mode is chosen once in config and is the same for every group; a group's own list is read as either a
 * whitelist or a blacklist accordingly.
 */
public enum RuleMode {
    WHITELIST,
    BLACKLIST;

    /**
     * Parse the config {@code mode} string case-insensitively, returning {@code fallback} for a blank or unrecognised
     * value so a typo never silently flips an operator into an unintended lock-down.
     */
    public static RuleMode fromConfig(@Nullable String raw, RuleMode fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "whitelist" -> WHITELIST;
            case "blacklist" -> BLACKLIST;
            default -> fallback;
        };
    }
}
