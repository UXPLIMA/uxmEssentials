package com.uxplima.uxmessentials.playerstate.domain;

/**
 * A freeze duration for {@code /ice <seconds>}, in seconds, clamped to a sane {@code 0..MAX_SECONDS} range in
 * the domain so the adapter only ever calls {@code Entity#setFreezeTicks} with a non-negative tick count. A
 * request below zero is treated as zero (which thaws the player); a request beyond the cap is held at the cap
 * so a typo cannot freeze someone for an absurd length of time. This is the cosmetic opposite of
 * {@link BurnDuration}, the powder-snow shiver instead of fire.
 *
 * @param seconds the clamped freeze duration in seconds
 */
public record FreezeDuration(int seconds) {

    /** Vanilla ticks per second, used to convert to the {@code setFreezeTicks} tick count. */
    public static final int TICKS_PER_SECOND = 20;

    /** The longest freeze {@code /ice} will set, to bound an accidental large argument. */
    public static final int MAX_SECONDS = 3_600;

    public FreezeDuration {
        if (seconds < 0) {
            seconds = 0;
        } else if (seconds > MAX_SECONDS) {
            seconds = MAX_SECONDS;
        }
    }

    /** A clamped duration from a raw seconds argument. */
    public static FreezeDuration ofSeconds(int seconds) {
        return new FreezeDuration(seconds);
    }

    /** The duration as a {@code setFreezeTicks} tick count. */
    public int ticks() {
        return seconds * TICKS_PER_SECOND;
    }
}
