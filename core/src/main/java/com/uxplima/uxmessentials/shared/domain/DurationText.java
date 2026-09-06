package com.uxplima.uxmessentials.shared.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Formats a {@link Duration} as a compact human-readable label for player-facing displays, for
 * example {@code 2d 3h}, {@code 1h 30m}, {@code 45s}. Components that are zero are omitted;
 * resolution stops at whole days (no weeks). Used by the vote cooldown and reminder displays to
 * fill {@code {time}} placeholders without pulling in any Bukkit type.
 */
public final class DurationText {

    private DurationText() {}

    /**
     * Render {@code duration} as a space-separated label of non-zero day/hour/minute/second
     * components. A zero or negative duration returns {@code "0s"}.
     *
     * <p>Examples: {@code PT90061S} → {@code "1d 1h 1m 1s"},
     * {@code PT5400S} → {@code "1h 30m"},
     * {@code PT45S} → {@code "45s"}.
     */
    public static String humanize(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds == 0) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        if (days > 0) out.append(days).append('d');
        if (hours > 0) {
            if (!out.isEmpty()) out.append(' ');
            out.append(hours).append('h');
        }
        if (minutes > 0) {
            if (!out.isEmpty()) out.append(' ');
            out.append(minutes).append('m');
        }
        if (seconds > 0) {
            if (!out.isEmpty()) out.append(' ');
            out.append(seconds).append('s');
        }
        return out.toString();
    }
}
