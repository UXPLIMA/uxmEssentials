package com.uxplima.uxmessentials.playerstate.application.port;

import java.time.LocalDate;
import java.util.UUID;

import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;

/**
 * Outbound port for the DB-backed playtime ledger. A per-day row holds a player's active (non-AFK) and AFK
 * seconds; the periodic sampler adds the sample interval to today's row for each online player, and the
 * {@code /playtime} breakdown reads the windowed totals back. The today/week/month/all-time buckets fall out as
 * range {@code SUM}s over the per-day rows, so the store keeps no aggregate state and needs no rollover.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. The database is the source of truth (this survives a world rollback); every method
 * is called off the tick thread (the sampler writes async, the command reads async). {@link #addSeconds} is a
 * single atomic upsert, so two samples for the same player-day serialise at the database rather than in the JVM.
 *
 * <p>The implementation lives in the persistence adapter; the playerstate context depends only on this port.
 */
public interface PlaytimeRepository {

    /**
     * Add {@code activeDelta} active seconds and {@code afkDelta} AFK seconds to the {@code (uuid, day)} row,
     * inserting the row at those values when it does not yet exist (an idempotent-keyed upsert, not a blind
     * insert). Both deltas are non-negative; a sample classifies a player as either active or AFK, so one delta is
     * the interval and the other is zero on any given tick.
     *
     * @param uuid the player whose ledger to credit
     * @param day the server-local calendar day the seconds accrued on
     * @param activeDelta non-negative active (non-AFK) seconds to add
     * @param afkDelta non-negative AFK seconds to add
     */
    void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta);

    /**
     * The windowed active/AFK totals for {@code uuid} as of {@code today}, today, the last seven days, the last
     * thirty days, and all time. Each window is a range {@code SUM} over the per-day rows; a player with no rows
     * yields {@link PlaytimeSummary#empty()}.
     *
     * @param uuid the player to summarise
     * @param today the server-local calendar day to anchor the today/week/month windows on
     * @return the player's playtime split across the four windows
     */
    PlaytimeSummary summaryOf(UUID uuid, LocalDate today);

    /** Delete every row for {@code uuid}, resetting their tracked playtime to nothing. A no-op when none exist. */
    void reset(UUID uuid);

    /**
     * Delete every row for every player, clearing the whole tracked ledger. The administrative companion to
     * {@link #reset(UUID)} behind {@code /playtime resetall}; a no-op on an already-empty store.
     */
    void resetAll();
}
