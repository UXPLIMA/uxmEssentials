package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A validated world-time request for {@code /time <set|add> <value>} and the {@code /day} / {@code /night}
 * aliases. The value is a tick count in {@code [0, 24000)} resolved from either a raw number or one of the
 * named keywords ({@code day}, {@code night}, {@code noon}, {@code midnight}, {@code sunrise}, {@code sunset}).
 *
 * <p>Modelling time as a validated value keeps the {@link ItemWorldError#BAD_TIME} decision in the domain:
 * {@code /day} is {@code set(1000)}, {@code /night} is {@code set(13000)}, and {@code /time add 100} advances.
 * A use case applies the mode against the world's current ticks; the spec only carries the validated value
 * and whether it is a set or an add.
 *
 * @param ticks the validated tick value ({@code 0..23999} for a set; any non-negative delta for an add)
 * @param mode whether to set the absolute time or add a delta
 */
public record TimeSpec(long ticks, Mode mode) {

    private static final long DAY_TICKS = 24_000L;

    /** Whether {@code /time} sets the absolute world time or adds a relative delta. */
    public enum Mode {
        SET,
        ADD
    }

    public TimeSpec {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be non-negative: " + ticks);
        }
        if (mode == Mode.SET && ticks >= DAY_TICKS) {
            throw new IllegalArgumentException("set ticks must be within a day: " + ticks);
        }
    }

    /** {@code /day}, daytime set. */
    public static TimeSpec day() {
        return new TimeSpec(1_000L, Mode.SET);
    }

    /** {@code /night}, nighttime set. */
    public static TimeSpec night() {
        return new TimeSpec(13_000L, Mode.SET);
    }

    /**
     * Parse {@code /time <mode> <value>} where {@code value} is a tick number or a named keyword. Returns
     * empty for an unparsable value or a set out of {@code [0,24000)}; a use case maps that to
     * {@link ItemWorldError#BAD_TIME}.
     */
    public static Optional<TimeSpec> parse(Mode mode, String rawValue) {
        if (mode == null || rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        Long keyword = keyword(value);
        long ticks;
        if (keyword != null) {
            ticks = keyword;
        } else {
            try {
                ticks = Long.parseLong(value);
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        }
        if (ticks < 0 || (mode == Mode.SET && ticks >= DAY_TICKS)) {
            return Optional.empty();
        }
        return Optional.of(new TimeSpec(ticks, mode));
    }

    private static @Nullable Long keyword(String value) {
        return switch (value) {
            case "day" -> 1_000L;
            case "noon" -> 6_000L;
            case "sunset" -> 12_000L;
            case "night" -> 13_000L;
            case "midnight" -> 18_000L;
            case "sunrise" -> 23_000L;
            default -> null;
        };
    }
}
