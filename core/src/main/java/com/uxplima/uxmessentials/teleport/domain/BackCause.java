package com.uxplima.uxmessentials.teleport.domain;

/**
 * What armed a {@code /back} return point. The distinction matters because death-back is gated by its
 * own permission ({@code uxmessentials.back.ondeath}) separately from teleport-back, so a server can
 * offer {@code /back} after a {@code /warp} but not after dying.
 */
public enum BackCause {

    /** Captured just before a teleport hop, the pre-teleport location. */
    TELEPORT,

    /** Captured at the point of death, only returnable with the death-back permission. */
    DEATH
}
