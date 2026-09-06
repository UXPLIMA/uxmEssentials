package com.uxplima.uxmessentials.shared.display;

/**
 * A surface a broadcast can be delivered on, shared by every context that pushes a rendered message to players
 * vote broadcasts, the rotating announcer, and future achievement notifications. The operator selects a set of
 * these in the relevant config; the adapter renders the resolved message once per recipient and pushes it to each
 * selected channel.
 *
 * <ul>
 *   <li>{@link #CHAT}, a normal chat line.
 *   <li>{@link #ACTION_BAR}, the above-hotbar action bar.
 *   <li>{@link #TITLE}: the large centred title.
 *   <li>{@link #SUBTITLE}, the smaller line beneath a title.
 *   <li>{@link #BOSS_BAR}, a transient boss bar.
 * </ul>
 */
public enum BroadcastChannel {
    CHAT,
    ACTION_BAR,
    TITLE,
    SUBTITLE,
    BOSS_BAR
}
