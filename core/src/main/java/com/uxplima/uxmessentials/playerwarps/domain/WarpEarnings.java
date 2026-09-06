package com.uxplima.uxmessentials.playerwarps.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Owner earnings that have accrued on a warp but not yet been paid out. The running tally a rented or sponsored
 * warp builds up between payouts. Modelled as an exact {@link BigDecimal} so the balance never picks up
 * binary floating-point drift, exactly like {@code warps.domain.WarpCost}.
 *
 * <p>Like every money-shaped domain value here it carries a bare {@link #currencyId} string and nothing from the
 * economy context: whether and how the accrued amount is settled is the application layer's decision, made only
 * when an economy provider is present. Keeping the earnings a pure value keeps the player-warps domain free of any
 * hard dependency on the economy context.
 *
 * @param amount the non-negative accrued balance
 * @param currencyId the currency the balance is denominated in
 */
public record WarpEarnings(BigDecimal amount, String currencyId) {

    public WarpEarnings {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("warp earnings must not be negative: " + amount);
        }
    }

    /** A freshly created, nothing-accrued balance in the given currency. */
    public static WarpEarnings zero(String currencyId) {
        return new WarpEarnings(BigDecimal.ZERO, currencyId);
    }

    /** A concrete, non-negative accrued balance in the given currency. */
    public static WarpEarnings of(BigDecimal amount, String currencyId) {
        return new WarpEarnings(amount, currencyId);
    }

    /** True when nothing has accrued, so a payout attempt can be skipped entirely. */
    public boolean isZero() {
        return amount.signum() == 0;
    }

    /**
     * Apply a change to the balance, keeping the same currency. A positive delta accrues more; a negative delta
     * settles part of it, but only as far as zero. A delta that would drive the balance negative is rejected,
     * since a warp can never owe money to its owner.
     */
    public WarpEarnings plus(BigDecimal delta) {
        Objects.requireNonNull(delta, "delta");
        return new WarpEarnings(amount.add(delta), currencyId);
    }
}
