package com.uxplima.uxmessentials.vote.domain;

/**
 * Controls how the vote-party counter is reset on a calendar boundary. {@link #NONE} means the
 * counter never auto-resets (it only resets when a party fires); {@link #DAILY} resets it at the
 * start of each server-local calendar day; {@link #WEEKLY} resets it at the start of each ISO week.
 */
public enum PartyResetSchedule {

    /** No periodic reset: the counter only resets when a party fires. */
    NONE,

    /** Reset the counter at the start of each calendar day (server timezone). */
    DAILY,

    /** Reset the counter at the start of each ISO week (Monday, server timezone). */
    WEEKLY
}
