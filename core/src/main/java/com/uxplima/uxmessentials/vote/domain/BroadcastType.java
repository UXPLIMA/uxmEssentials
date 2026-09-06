package com.uxplima.uxmessentials.vote.domain;

/**
 * How widely a credited vote announces itself. The operator picks one of these in the vote config and
 * {@link com.uxplima.uxmessentials.vote.application.BroadcastDecision} turns it into a yes/no for any
 * given vote: the type carries no behaviour itself, it is the policy input.
 *
 * <ul>
 *   <li>{@link #NONE}: never broadcast a thank-you; the vote is still recorded and rewarded silently.
 *   <li>{@link #EVERY_VOTE}: broadcast every credited vote, online or offline.
 *   <li>{@link #ONLINE_ONLY}: broadcast only when the voter is online (the historic default behaviour).
 *   <li>{@link #COOLDOWN_PER_PLAYER}, broadcast for an online voter, but at most once per configured
 *       window per player, so a player voting on several sites in quick succession announces once.
 *   <li>{@link #FIRST_VOTE_OF_DAY}: broadcast only the voter's first vote of the calendar day.
 * </ul>
 */
public enum BroadcastType {
    NONE,
    EVERY_VOTE,
    ONLINE_ONLY,
    COOLDOWN_PER_PLAYER,
    FIRST_VOTE_OF_DAY
}
