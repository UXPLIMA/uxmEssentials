package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * What the rent sweep should do with one warp this pass, decided purely from its {@link WarpStatus} and
 * {@link RentState} against the configured rent term.
 *
 * <ul>
 *   <li>{@link #DUE}. An {@link WarpStatus#ACTIVE ACTIVE} warp whose paid term has lapsed: attempt a charge, and
 *       on failure suspend it.
 *   <li>{@link #RETRY}. A {@link WarpStatus#SUSPENDED SUSPENDED} warp still inside its grace window: re-attempt a
 *       charge, restoring it to {@code ACTIVE} on success and otherwise leaving it suspended.
 *   <li>{@link #ARCHIVE}. A {@code SUSPENDED} warp whose grace window has lapsed: retire it to
 *       {@link WarpStatus#ARCHIVED ARCHIVED} (recoverable, never a hard delete).
 *   <li>{@link #NONE}. Nothing to do: the sub-group is off, the warp is still paid through, or it is already
 *       archived.
 * </ul>
 */
public enum RentDecision {
    DUE,
    RETRY,
    ARCHIVE,
    NONE
}
