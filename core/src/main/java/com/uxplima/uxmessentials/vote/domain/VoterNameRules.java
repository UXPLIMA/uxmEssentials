package com.uxplima.uxmessentials.vote.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.Nullable;

/**
 * The validity rules a voter name must satisfy before a vote is credited: a maximum length and an
 * optional whitelist pattern. A Votifier payload can arrive with a junk username, empty, the literal
 * string {@code "null"}, an over-long value, or one carrying characters the server's name policy
 * forbids, and crediting such a name spawns a phantom entry on the leaderboard and pays a reward into
 * a player that does not exist. These rules let the adapter reject those payloads at the boundary.
 *
 * <p>Pure value, no I/O, no Bukkit. The optional pattern, when present, is matched against the whole
 * name ({@link java.util.regex.Matcher#matches()}); when absent only the length and the always-on
 * blank/{@code "null"} checks apply.
 *
 * @param maxLength the longest a name may be, inclusive; at least one
 * @param pattern   an optional whitelist the whole name must match; empty means length-only validation
 */
public record VoterNameRules(int maxLength, Optional<Pattern> pattern) {

    public VoterNameRules {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be at least 1, got " + maxLength);
        }
        Objects.requireNonNull(pattern, "pattern");
    }

    /**
     * Whether {@code name} is a usable voter name: non-null, non-blank, not the literal {@code "null"},
     * within {@link #maxLength}, and, when a pattern is configured, matching it in full. A {@code null}
     * is accepted as an argument (a Votifier payload may carry one) and rejected as invalid.
     */
    public boolean isValid(@Nullable String name) {
        return name != null
                && !name.isBlank()
                && !name.equals("null")
                && name.length() <= maxLength
                && (pattern.isEmpty() || pattern.get().matcher(name).matches());
    }

    /**
     * Build rules from a max length and a regex string. A blank or {@code null} pattern yields
     * length-only validation. A malformed regex is tolerated: it compiles to no pattern (so only the
     * length and blank/{@code "null"} checks apply) rather than failing construction: the adapter is
     * expected to log the bad regex so the operator can fix it.
     */
    public static VoterNameRules of(int maxLength, String patternOrBlank) {
        if (patternOrBlank == null || patternOrBlank.isBlank()) {
            return new VoterNameRules(maxLength, Optional.empty());
        }
        try {
            return new VoterNameRules(maxLength, Optional.of(Pattern.compile(patternOrBlank)));
        } catch (PatternSyntaxException malformed) {
            return new VoterNameRules(maxLength, Optional.empty());
        }
    }
}
