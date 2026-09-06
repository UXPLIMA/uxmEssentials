package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;

/**
 * Renders a {@link Duration} into the compact {@code 1h2m3s} form the {@code afk_duration} and {@code
 * kit_cooldown_<id>} placeholders carry. Whole seconds only. Sub-second precision is meaningless on a
 * placeholder surface, and a non-positive duration renders {@code 0s}. Only non-zero units appear, so a
 * two-hour wait reads {@code 2h} rather than {@code 2h0m0s} and ninety seconds reads {@code 1m30s}.
 */
final class PlaceholderDurations {

    private PlaceholderDurations() {}

    /** {@code duration} as {@code 1d2h3m4s}, trimming leading zero units; {@code 0s} when non-positive. */
    static String compact(Duration duration) {
        long total = Math.max(0, duration.toSeconds());
        if (total == 0) {
            return "0s";
        }
        long days = total / 86_400;
        long hours = total % 86_400 / 3_600;
        long minutes = total % 3_600 / 60;
        long seconds = total % 60;
        StringBuilder out = new StringBuilder();
        append(out, days, 'd');
        append(out, hours, 'h');
        append(out, minutes, 'm');
        append(out, seconds, 's');
        return out.toString();
    }

    private static void append(StringBuilder out, long value, char unit) {
        if (value > 0) {
            out.append(value).append(unit);
        }
    }
}
