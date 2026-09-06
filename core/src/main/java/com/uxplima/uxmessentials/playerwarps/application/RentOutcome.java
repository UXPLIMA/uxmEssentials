package com.uxplima.uxmessentials.playerwarps.application;

/**
 * What one warp's rent settlement did this pass, returned by {@code SettleRent} so the sweep can tally and log
 * without re-reading the row.
 *
 * <ul>
 *   <li>{@link #RENEWED}: an active warp's rent was collected and its term advanced.
 *   <li>{@link #RESTORED}: a suspended warp's owner found the funds; it was recharged and returned to active.
 *   <li>{@link #SUSPENDED}: an active warp could not pay; it was suspended with an archive deadline.
 *   <li>{@link #ARCHIVED}: a suspended warp's grace window lapsed; it was archived (recoverable).
 *   <li>{@link #UNCHANGED}. Nothing happened: exempt, not enrolled, still paid through, a still-failing retry, or
 *       the sub-group off.
 * </ul>
 */
public enum RentOutcome {
    RENEWED,
    RESTORED,
    SUSPENDED,
    ARCHIVED,
    UNCHANGED
}
