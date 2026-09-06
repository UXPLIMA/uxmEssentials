package com.uxplima.uxmessentials.playerstate.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * A per-player client-side time for {@code /ptime}: either a fixed tick (a relative time the client renders
 * without changing world time) or a reset back to the server's actual time. Modelled in the domain so the
 * adapter only ever calls {@code Player#setPlayerTime} / {@code resetPlayerTime} with a validated value.
 *
 * @param ticks the fixed time of day in ticks ({@code 0..23999}); ignored when {@code reset} is true
 * @param reset whether to clear the override and follow world time again
 */
public record PersonalTime(long ticks, boolean reset) {

    /** A day's length in ticks; a fixed value is taken modulo this. */
    public static final long TICKS_PER_DAY = 24_000L;

    public PersonalTime {
        if (!reset) {
            ticks = Math.floorMod(ticks, TICKS_PER_DAY);
        }
    }

    /** The reset form, follow world time again. */
    public static PersonalTime resetTime() {
        return new PersonalTime(0L, true);
    }

    /**
     * Parse a raw {@code /ptime} argument: {@code reset}, the named presets {@code day}/{@code night}/
     * {@code noon}/{@code midnight}/{@code sunrise}/{@code sunset}, or a raw tick count. Returns empty when
     * the argument is neither a known word nor a parseable number.
     */
    public static Optional<PersonalTime> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String token = raw.strip().toLowerCase(Locale.ROOT);
        return switch (token) {
            case "reset" -> Optional.of(resetTime());
            case "day" -> Optional.of(fixed(1_000L));
            case "noon" -> Optional.of(fixed(6_000L));
            case "sunset" -> Optional.of(fixed(12_000L));
            case "night" -> Optional.of(fixed(14_000L));
            case "midnight" -> Optional.of(fixed(18_000L));
            case "sunrise" -> Optional.of(fixed(23_000L));
            default -> parseTicks(token);
        };
    }

    private static PersonalTime fixed(long ticks) {
        return new PersonalTime(ticks, false);
    }

    private static Optional<PersonalTime> parseTicks(String token) {
        try {
            return Optional.of(fixed(Long.parseLong(token)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
