package com.uxplima.uxmessentials.survival.domain;

/**
 * The pure percentage roll behind a head drop: a chance in {@code [0, 100]} that is compared against a bounded random
 * draw. Keeping the comparison here, off any random source, leaves the decision deterministic under test, the adapter
 * draws a value in {@code [0, }{@link #RESOLUTION}{@code )} through its seedable {@code RandomSource} and asks whether
 * that draw {@link #drops(int) drops}, so a test can pin an exact draw against an exact chance and assert drop or
 * no-drop with no real randomness.
 *
 * <p>The chance is resolved to hundredths of a percent ({@link #RESOLUTION} basis points), matching the two-decimal
 * chances head-drop configs conventionally carry ({@code 12.50}); a draw strictly below the resolved threshold drops.
 * The two extremes short-circuit: a chance of zero {@link #isNever() never} drops and a chance of one hundred
 * {@link #isAlways() always} does, independent of the draw.
 */
public record DropChance(double percent) {

    /** The draw space: hundredths of a percent, so a chance of {@code 12.50}% resolves to a threshold of {@code 1250}. */
    public static final int RESOLUTION = 10_000;

    public DropChance {
        if (!Double.isFinite(percent)) {
            throw new IllegalArgumentException("percent must be finite: " + percent);
        }
        percent = Math.max(0.0, Math.min(100.0, percent));
    }

    /** Whether this chance never drops (a percentage of zero). */
    public boolean isNever() {
        return percent <= 0.0;
    }

    /** Whether this chance always drops (a percentage of one hundred). */
    public boolean isAlways() {
        return percent >= 100.0;
    }

    /**
     * Whether a random {@code draw} in {@code [0, }{@link #RESOLUTION}{@code )} lands within this chance.
     *
     * @param draw the bounded random value, at least zero and below {@link #RESOLUTION}
     * @return {@code true} when the draw is below the resolved threshold (or the chance always drops)
     */
    public boolean drops(int draw) {
        if (draw < 0 || draw >= RESOLUTION) {
            throw new IllegalArgumentException("draw must be in [0, " + RESOLUTION + "): " + draw);
        }
        if (isNever()) {
            return false;
        }
        if (isAlways()) {
            return true;
        }
        int threshold = (int) Math.round(percent * (RESOLUTION / 100.0));
        return draw < threshold;
    }
}
