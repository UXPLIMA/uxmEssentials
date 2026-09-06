package com.uxplima.uxmessentials.moderation.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and renders the human-friendly duration strings the sanction commands accept, {@code 30s},
 * {@code 15m}, {@code 2h}, {@code 7d}, {@code 4w}, or a concatenation like {@code 1h30m}. A blank string or
 * the literal {@code permanent}/{@code perm} parses to {@link Optional#empty()} (a permanent sanction); a
 * malformed or non-positive string parses to no value, which a use case maps to {@code BAD_DURATION}.
 *
 * <p>Pure and self-contained: the moderation context owns its own duration grammar rather than depending on
 * an adapter, so {@code /tempmute 1h30m} and {@code /tempban 7d} are tested entirely in {@code :core}.
 */
public final class SanctionDuration {

    private static final Pattern UNIT = Pattern.compile("(\\d+)([smhdw])");

    private SanctionDuration() {}

    /**
     * Parse {@code raw} into an optional span: empty for a permanent sanction ({@code permanent}/{@code perm}
     * or blank), a present positive {@link Duration} for a timed one. A string that contains no valid unit
     * token, or sums to zero/negative, yields a malformed {@link Parsed}, callers distinguish "permanent"
     * from "malformed" through the {@link Parsed} record.
     */
    public static Parsed parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.equals("permanent") || trimmed.equals("perm")) {
            return Parsed.permanent();
        }
        Matcher matcher = UNIT.matcher(trimmed);
        Duration total = Duration.ZERO;
        int matchedEnd = 0;
        boolean any = false;
        while (matcher.find()) {
            if (matcher.start() != matchedEnd) {
                return Parsed.ofMalformed();
            }
            total = total.plus(
                    unit(Long.parseLong(matcher.group(1)), matcher.group(2).charAt(0)));
            matchedEnd = matcher.end();
            any = true;
        }
        if (!any || matchedEnd != trimmed.length() || total.isZero() || total.isNegative()) {
            return Parsed.ofMalformed();
        }
        return Parsed.timed(total);
    }

    private static Duration unit(long amount, char unit) {
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            case 'w' -> Duration.ofDays(amount * 7L);
            default -> Duration.ZERO;
        };
    }

    /** Render a span as a compact {@code 1h30m}-style label for audit lines and player feedback. */
    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds == 0) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        seconds = appendUnit(out, seconds, 604_800L, 'w');
        seconds = appendUnit(out, seconds, 86_400L, 'd');
        seconds = appendUnit(out, seconds, 3_600L, 'h');
        seconds = appendUnit(out, seconds, 60L, 'm');
        appendUnit(out, seconds, 1L, 's');
        return out.toString();
    }

    private static long appendUnit(StringBuilder out, long seconds, long unitSeconds, char suffix) {
        long count = seconds / unitSeconds;
        if (count > 0) {
            out.append(count).append(suffix);
        }
        return seconds % unitSeconds;
    }

    /**
     * The outcome of {@link #parse}: a permanent sanction, a timed one with a positive {@link Duration}, or a
     * malformed string. Modelling the three cases keeps the {@code BAD_DURATION} decision out of the parser's
     * return type.
     */
    public record Parsed(boolean malformed, Optional<Duration> duration) {

        static Parsed permanent() {
            return new Parsed(false, Optional.empty());
        }

        static Parsed timed(Duration duration) {
            return new Parsed(false, Optional.of(duration));
        }

        static Parsed ofMalformed() {
            return new Parsed(true, Optional.empty());
        }

        /** True for a permanent sanction (no duration, well-formed). */
        public boolean isPermanent() {
            return !malformed && duration.isEmpty();
        }
    }
}
