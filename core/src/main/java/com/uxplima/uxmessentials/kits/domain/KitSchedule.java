package com.uxplima.uxmessentials.kits.domain;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An optional availability window deciding when a kit may be claimed: a rotating or limited-time kit. Four
 * independent constraints, each optional and all ANDed together:
 *
 * <ul>
 *   <li>{@code days}: the weekdays the kit is claimable on; empty means every day.
 *   <li>{@code dailyStart}/{@code dailyEnd}, a daily time-of-day window {@code [start, end)}. When the start
 *       is later than the end the window wraps past midnight (e.g. {@code 22:00} to {@code 06:00}). A bound left
 *       absent extends the window to the start ({@code 00:00}) or end of the day.
 *   <li>{@code from}/{@code until}, an absolute calendar window {@code [from, until)} for a one-off event.
 * </ul>
 *
 * <p>The {@link #always() empty schedule} imposes no constraint and is the default every kit carries, so a kit
 * authored with no {@code schedule} block stays claimable exactly as before. All times are evaluated in the
 * server's local zone: the zone an operator naturally authors the window in.
 *
 * @param days the weekdays the kit may be claimed on, in enum order; empty for every day
 * @param dailyStart the inclusive daily window start, or empty for the start of the day
 * @param dailyEnd the exclusive daily window end, or empty for the end of the day
 * @param from the inclusive absolute start of the kit's availability, or empty for no lower bound
 * @param until the exclusive absolute end of the kit's availability, or empty for no upper bound
 */
public record KitSchedule(
        Set<DayOfWeek> days,
        Optional<LocalTime> dailyStart,
        Optional<LocalTime> dailyEnd,
        Optional<LocalDateTime> from,
        Optional<LocalDateTime> until) {

    private static final KitSchedule ALWAYS =
            new KitSchedule(Set.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public KitSchedule {
        Objects.requireNonNull(days, "days");
        Objects.requireNonNull(dailyStart, "dailyStart");
        Objects.requireNonNull(dailyEnd, "dailyEnd");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(until, "until");
        days = days.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(days));
    }

    /** The unconstrained schedule: a kit claimable at any time. The default every kit carries. */
    public static KitSchedule always() {
        return ALWAYS;
    }

    /** True when this schedule imposes no constraint at all (the {@link #always()} default). */
    public boolean isAlways() {
        return days.isEmpty() && dailyStart.isEmpty() && dailyEnd.isEmpty() && from.isEmpty() && until.isEmpty();
    }

    /** Whether a claim at {@code now} (server-local) falls inside every constraint this schedule sets. */
    public boolean isAvailableAt(LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        if (from.isPresent() && now.isBefore(from.get())) {
            return false;
        }
        if (until.isPresent() && !now.isBefore(until.get())) {
            return false;
        }
        if (!days.isEmpty() && !days.contains(now.getDayOfWeek())) {
            return false;
        }
        if (dailyStart.isPresent() || dailyEnd.isPresent()) {
            return inDailyWindow(now.toLocalTime());
        }
        return true;
    }

    private boolean inDailyWindow(LocalTime time) {
        LocalTime start = dailyStart.orElse(LocalTime.MIN);
        LocalTime end = dailyEnd.orElse(LocalTime.MAX);
        if (!start.isAfter(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        // start later than end: the window wraps past midnight (e.g. 22:00 to 06:00).
        return !time.isBefore(start) || time.isBefore(end);
    }
}
