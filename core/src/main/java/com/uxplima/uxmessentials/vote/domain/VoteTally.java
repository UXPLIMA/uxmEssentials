package com.uxplima.uxmessentials.vote.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.Objects;

/**
 * Accumulated vote counts for one player across every tracked period. Each periodic bucket (daily,
 * weekly, monthly) carries a "key" that identifies the current calendar window; when a vote arrives
 * in a new window the bucket resets to zero and adopts the new key. The alltime bucket never resets.
 *
 * <p>Period keys:
 * <ul>
 *   <li>{@code dayKey}, {@link LocalDate#toEpochDay()} for the vote date.
 *   <li>{@code weekKey}. {@code (ISO week-based year * 100) + ISO week-of-year}, giving a unique
 *       integer per ISO week that does not collide across years.
 *   <li>{@code monthKey}: {@code year * 12 + monthValue} (1-based), increasing monotonically.
 * </ul>
 *
 * <p>All fields are non-negative. The compact constructor enforces this so that no invalid tally
 * can be persisted or deserialized into the application layer.
 *
 * <p>The streak fields track consecutive-day voting independently of the calendar buckets.
 * {@code currentStreak} is the run of days the player has voted on, {@code bestStreak} the longest
 * run ever reached, and {@code streakDayKey} the epoch-day of the most recent vote that touched the
 * streak, so a second vote on the same day leaves the streak unchanged while the first vote of the
 * next day extends it.
 *
 * @param alltime accumulated votes across all time
 * @param daily votes in the current calendar day
 * @param weekly votes in the current ISO week
 * @param monthly votes in the current calendar month
 * @param dayKey the epoch-day of the period the daily bucket covers
 * @param weekKey the ISO-week identifier ({@code weekBasedYear * 100 + weekOfYear}) the weekly
 *     bucket covers
 * @param monthKey the month identifier ({@code year * 12 + monthValue}) the monthly bucket covers
 * @param currentStreak the current run of consecutive voting days
 * @param bestStreak the longest run of consecutive voting days ever reached
 * @param streakDayKey the epoch-day of the most recent vote counted toward the streak
 */
public record VoteTally(
        long alltime,
        long daily,
        long weekly,
        long monthly,
        long dayKey,
        long weekKey,
        long monthKey,
        long currentStreak,
        long bestStreak,
        long streakDayKey) {

    public VoteTally {
        if (alltime < 0) throw new IllegalArgumentException("alltime must not be negative: " + alltime);
        if (daily < 0) throw new IllegalArgumentException("daily must not be negative: " + daily);
        if (weekly < 0) throw new IllegalArgumentException("weekly must not be negative: " + weekly);
        if (monthly < 0) throw new IllegalArgumentException("monthly must not be negative: " + monthly);
        if (dayKey < 0) throw new IllegalArgumentException("dayKey must not be negative: " + dayKey);
        if (weekKey < 0) throw new IllegalArgumentException("weekKey must not be negative: " + weekKey);
        if (monthKey < 0) throw new IllegalArgumentException("monthKey must not be negative: " + monthKey);
        if (currentStreak < 0)
            throw new IllegalArgumentException("currentStreak must not be negative: " + currentStreak);
        if (bestStreak < 0) throw new IllegalArgumentException("bestStreak must not be negative: " + bestStreak);
        if (streakDayKey < 0) throw new IllegalArgumentException("streakDayKey must not be negative: " + streakDayKey);
    }

    /** A brand-new tally with every bucket, key, and streak field at zero (used for a player's first vote). */
    public static VoteTally empty() {
        return new VoteTally(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    /**
     * Produce a new tally that records one more vote that arrived at {@code now} in {@code zone},
     * with no grace window: a vote must land the very next calendar day to extend the streak.
     * Equivalent to {@link #recordVote(Instant, ZoneId, int)} with {@code graceDays == 0}.
     *
     * @param now the wall-clock instant of the incoming vote
     * @param zone the timezone used to derive calendar boundaries
     * @return the updated tally (this tally is not mutated)
     */
    public VoteTally recordVote(Instant now, ZoneId zone) {
        return recordVote(now, zone, 0);
    }

    /**
     * Produce a new tally that records one more vote that arrived at {@code now} in {@code zone}.
     * Each periodic bucket is rolled over (reset to zero) if the current calendar window has
     * advanced since the stored key was written; then the bucket is incremented. Alltime always
     * increments.
     *
     * <p>The streak transition runs alongside the bucket rollover: a vote on the same day as the
     * last streak vote leaves the streak unchanged, a vote within {@code 1 + graceDays} days extends
     * it by one, and a longer gap resets it to one. {@code graceDays} below zero is treated as zero,
     * so {@code graceDays == 1} lets a player miss a single day without breaking their run.
     *
     * @param now the wall-clock instant of the incoming vote
     * @param zone the timezone used to derive calendar boundaries
     * @param graceDays extra missed days tolerated between consecutive votes before the streak breaks
     * @return the updated tally (this tally is not mutated)
     */
    public VoteTally recordVote(Instant now, ZoneId zone, int graceDays) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");

        LocalDate date = LocalDate.ofInstant(now, zone);
        long currentDayKey = date.toEpochDay();
        long currentWeekKey = isoWeekKey(date);
        long currentMonthKey = monthKey(date);

        long newDaily = (currentDayKey == dayKey) ? daily + 1 : 1L;
        long newWeekly = (currentWeekKey == weekKey) ? weekly + 1 : 1L;
        long newMonthly = (currentMonthKey == monthKey) ? monthly + 1 : 1L;

        int grace = Math.max(0, graceDays);
        long newCurrentStreak = nextStreak(currentDayKey, grace);
        long newBestStreak = Math.max(bestStreak, newCurrentStreak);

        return new VoteTally(
                alltime + 1,
                newDaily,
                newWeekly,
                newMonthly,
                currentDayKey,
                currentWeekKey,
                currentMonthKey,
                newCurrentStreak,
                newBestStreak,
                currentDayKey);
    }

    private long nextStreak(long currentDayKey, int grace) {
        if (streakDayKey == 0) {
            return 1L;
        }
        long delta = currentDayKey - streakDayKey;
        if (delta == 0) {
            return currentStreak;
        }
        if (delta >= 1 && delta <= 1 + grace) {
            return currentStreak + 1;
        }
        return 1L;
    }

    /**
     * Returns the accumulated vote count for the requested period. For periodic buckets this
     * reflects the current window as stored. The caller is responsible for reading a fresh tally
     * from the repository before rendering.
     */
    public long countFor(VotePeriod period) {
        Objects.requireNonNull(period, "period");
        return switch (period) {
            case ALLTIME -> alltime;
            case DAILY -> daily;
            case WEEKLY -> weekly;
            case MONTHLY -> monthly;
        };
    }

    // ISO week key: (week-based year * 100) + ISO week-of-year.
    // Using IsoFields so the year used is the week-based year (which can differ from the calendar
    // year in the first/last days of January/December).
    private static long isoWeekKey(LocalDate date) {
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return (long) weekYear * 100 + weekOfYear;
    }

    private static long monthKey(LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        return (long) ym.getYear() * 12 + ym.getMonthValue();
    }
}
