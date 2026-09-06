package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Per-warp overrides of the context-wide teleport warmup and cooldown, grouped so the aggregate carries one
 * {@code WarpTimingOverrides} field instead of two loose {@link Optional}s. An absent override means "use the
 * configured default", modelled by an empty {@link Optional} rather than a sentinel, so a warp that has never
 * customised its timing is one unambiguous state. A present value is a non-negative duration in seconds; a
 * negative override is nonsensical (a warp cannot warm up for less than no time) and is rejected at construction.
 *
 * @param warmupSeconds the warmup this warp uses instead of the default, if overridden
 * @param cooldownSeconds the cooldown this warp uses instead of the default, if overridden
 */
public record WarpTimingOverrides(Optional<Double> warmupSeconds, Optional<Double> cooldownSeconds) {

    public WarpTimingOverrides {
        Objects.requireNonNull(warmupSeconds, "warmupSeconds");
        Objects.requireNonNull(cooldownSeconds, "cooldownSeconds");
        requireNonNegative(warmupSeconds, "warmup");
        requireNonNegative(cooldownSeconds, "cooldown");
    }

    /** A warp with no timing overrides, both warmup and cooldown fall back to the configured defaults. */
    public static WarpTimingOverrides none() {
        return new WarpTimingOverrides(Optional.empty(), Optional.empty());
    }

    private static void requireNonNegative(Optional<Double> value, String field) {
        value.ifPresent(seconds -> {
            if (seconds < 0) {
                throw new IllegalArgumentException(field + " override must not be negative: " + seconds);
            }
        });
    }
}
