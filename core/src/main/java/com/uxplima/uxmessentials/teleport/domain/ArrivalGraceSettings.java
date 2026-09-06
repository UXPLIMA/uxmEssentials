package com.uxplima.uxmessentials.teleport.domain;

/**
 * The tuning for the post-arrival grace window an {@code /rtp} applies once it lands: how long the shield
 * lasts and which of the three protections are switched on. A pure value read from config and handed to the
 * grace adapter, so the "what to apply" decision lives in one typed place rather than scattered config reads.
 *
 * @param seconds the length of the grace window in seconds; {@code 0} disables the grace entirely
 * @param resistance whether to apply a Resistance potion effect for the window
 * @param slowFalling whether to apply a Slow-Falling potion effect for the window
 * @param blockFallDamage whether to cancel fall damage outright for the duration of the window
 */
public record ArrivalGraceSettings(long seconds, boolean resistance, boolean slowFalling, boolean blockFallDamage) {

    public ArrivalGraceSettings {
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds must be >= 0: " + seconds);
        }
    }

    /** True when the grace does anything at all, a positive window with at least one protection enabled. */
    public boolean enabled() {
        return seconds > 0 && (resistance || slowFalling || blockFallDamage);
    }

    /** The window length expressed in ticks (20 per second), for the potion-effect duration. */
    public int durationTicks() {
        return Math.toIntExact(seconds * 20);
    }
}
