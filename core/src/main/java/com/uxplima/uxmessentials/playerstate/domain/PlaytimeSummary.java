package com.uxplima.uxmessentials.playerstate.domain;

import java.time.Duration;

/**
 * A read-only roll-up of one player's tracked playtime, split into active (non-AFK) and AFK seconds across four
 * windows: today, the last seven days, the last thirty days, and all time. The repository computes each window as
 * a range {@code SUM} over the per-day rows, so this carries no row detail. Only the totals the {@code /playtime}
 * breakdown renders.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>immutable value</b>. A snapshot built once per query from a consistent set of {@code SUM}s; safe
 * to hand across threads. The durable source of truth is the database (the per-day ledger), never this object.
 *
 * <p>Every field is a non-negative {@link Duration}. A player with no tracked rows yields {@link #empty()}, all
 * zero, which the command still renders (the breakdown of a never-sampled player reads as a clean zero rather
 * than no answer at all).
 *
 * @param todayActive active (non-AFK) time accrued on the server's current calendar day
 * @param todayAfk AFK time accrued on the server's current calendar day
 * @param weekActive active time accrued over the last seven calendar days (inclusive of today)
 * @param weekAfk AFK time accrued over the last seven calendar days (inclusive of today)
 * @param monthActive active time accrued over the last thirty calendar days (inclusive of today)
 * @param monthAfk AFK time accrued over the last thirty calendar days (inclusive of today)
 * @param totalActive active time accrued over every tracked day
 * @param totalAfk AFK time accrued over every tracked day
 */
public record PlaytimeSummary(
        Duration todayActive,
        Duration todayAfk,
        Duration weekActive,
        Duration weekAfk,
        Duration monthActive,
        Duration monthAfk,
        Duration totalActive,
        Duration totalAfk) {

    public PlaytimeSummary {
        requireNonNegative(todayActive, "todayActive");
        requireNonNegative(todayAfk, "todayAfk");
        requireNonNegative(weekActive, "weekActive");
        requireNonNegative(weekAfk, "weekAfk");
        requireNonNegative(monthActive, "monthActive");
        requireNonNegative(monthAfk, "monthAfk");
        requireNonNegative(totalActive, "totalActive");
        requireNonNegative(totalAfk, "totalAfk");
    }

    /** A summary with every window at zero, for a player who has no tracked rows yet. */
    public static PlaytimeSummary empty() {
        Duration zero = Duration.ZERO;
        return new PlaytimeSummary(zero, zero, zero, zero, zero, zero, zero, zero);
    }

    /** Build a summary from raw second counts (the shape the repository's range SUMs return). */
    public static PlaytimeSummary ofSeconds(
            long todayActive,
            long todayAfk,
            long weekActive,
            long weekAfk,
            long monthActive,
            long monthAfk,
            long totalActive,
            long totalAfk) {
        return new PlaytimeSummary(
                Duration.ofSeconds(todayActive),
                Duration.ofSeconds(todayAfk),
                Duration.ofSeconds(weekActive),
                Duration.ofSeconds(weekAfk),
                Duration.ofSeconds(monthActive),
                Duration.ofSeconds(monthAfk),
                Duration.ofSeconds(totalActive),
                Duration.ofSeconds(totalAfk));
    }

    /** All-time active + AFK combined: the closest DB-backed analogue to the vanilla lifetime play-one-minute stat. */
    public Duration totalCombined() {
        return totalActive.plus(totalAfk);
    }

    private static void requireNonNegative(Duration value, String field) {
        if (value == null) {
            throw new NullPointerException(field);
        }
        if (value.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
    }
}
