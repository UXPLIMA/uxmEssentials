package com.uxplima.uxmessentials.teleport.domain;

/**
 * A coarse, Bukkit-free classification of why a vanilla teleport happened, used by {@link
 * BackCapturePolicy} to decide whether a teleport should overwrite a player's {@code /back} point. The
 * adapter maps Paper's {@code PlayerTeleportEvent.TeleportCause} onto these so the back-capture decision
 * stays pure, the domain never sees a Bukkit enum.
 *
 * <p>The names mirror the vanilla causes an operator reasons about in {@code teleport.conf}'s
 * {@code back.ignored-causes} list (a misclick on an ender pearl should not erase where you were), so the
 * config value {@code ender_pearl} resolves to {@link #ENDER_PEARL} by simple upper-casing.
 */
public enum TeleportCauseCategory {

    /** A thrown ender pearl landing. */
    ENDER_PEARL,

    /** Eating a chorus fruit. */
    CHORUS_FRUIT,

    /** A command-driven teleport (vanilla {@code /tp}, {@code /spreadplayers}, plugins). */
    COMMAND,

    /** Stepping through a nether or end portal. */
    PORTAL,

    /** A teleport caused by the player riding or dismounting an entity. */
    DISMOUNT,

    /** Any other teleport cause not separately classified. */
    OTHER
}
