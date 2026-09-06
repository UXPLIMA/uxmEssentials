package com.uxplima.uxmessentials.teleport.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled gate to the moderation context: whether a player is currently jailed, in which case their
 * self-initiated teleports, {@code /home}, {@code /tpa}, {@code /rtp}, {@code /back}, {@code /spawn} and the
 * homes/warps verbs that delegate here, are blocked (docs/permissions.md, a jailed player is blocked
 * regardless of the teleport nodes). The teleport context does not own jail state; it asks this port.
 *
 * <p>The coupling is soft: when the moderation module is disabled (or has not yet landed) the wiring binds
 * {@link #NEVER}, so teleport degrades to "no one is jailed" rather than failing. This mirrors the messaging
 * context's {@code MutePolicy} seam, the same degrade-when-the-other-module-is-off pattern.
 */
public interface JailGate {

    /** A gate under which no one is ever jailed: the binding when moderation is disabled. */
    JailGate NEVER = who -> false;

    /** True when {@code who} is currently jailed and may not self-teleport. */
    boolean isJailed(PlayerRef who);
}
