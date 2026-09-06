package com.uxplima.uxmessentials.playerstate.application;

import java.time.Duration;

/**
 * Formats a {@link Duration} as the compact {@code Nd Nh Nm} string the {@code /playtime} breakdown lines embed
 * (e.g. {@code 2d 5h 13m}). Seconds are intentionally dropped. The sampler's resolution is a minute or coarser,
 * and the breakdown reads as wall-clock playtime, not a stopwatch. A zero duration renders as {@code 0m} so a
 * never-played window still produces a value the message can interpolate.
 */
final class PlaytimeFormat {

    private static final long MINUTES_PER_HOUR = 60L;
    private static final long HOURS_PER_DAY = 24L;

    private PlaytimeFormat() {}

    /** Render {@code duration} as {@code Nd Nh Nm}, omitting leading zero days/hours but always keeping minutes. */
    static String compact(Duration duration) {
        long totalMinutes = Math.max(0L, duration.toMinutes());
        long days = totalMinutes / (MINUTES_PER_HOUR * HOURS_PER_DAY);
        long hours = (totalMinutes / MINUTES_PER_HOUR) % HOURS_PER_DAY;
        long minutes = totalMinutes % MINUTES_PER_HOUR;
        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            out.append(hours).append("h ");
        }
        out.append(minutes).append('m');
        return out.toString();
    }
}
