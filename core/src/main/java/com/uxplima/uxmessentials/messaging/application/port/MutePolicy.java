package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled gate to the moderation context: whether a player is currently muted, in which case their
 * outbound {@code /msg}, {@code /reply}, {@code /mail send}, and {@code /helpop} are blocked
 * (docs/permissions.md: a muted player is blocked regardless of the messaging nodes). The messaging context
 * does not own mute state; it asks this port.
 *
 * <p>The coupling is soft: when the moderation module is disabled (or has not yet landed) the wiring binds
 * {@link #NEVER}, so messaging degrades to "no one is muted" rather than failing. This is the same
 * degrade-when-the-other-module-is-off pattern the warps cost uses against economy.
 */
public interface MutePolicy {

    /** A policy under which no one is ever muted: the binding when moderation is disabled. */
    MutePolicy NEVER = who -> false;

    /** True when {@code who} is currently muted and may not send outbound messaging. */
    boolean isMuted(PlayerRef who);
}
