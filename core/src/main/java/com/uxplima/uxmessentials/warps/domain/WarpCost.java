package com.uxplima.uxmessentials.warps.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The optional price of using a warp, modelled as an exact {@link BigDecimal} so currency amounts never
 * pick up binary floating-point error. A warp without a cost is represented by {@link #free()} rather than
 * a {@code null} amount, so the "no charge" case is a first-class value the use case can branch on without
 * a null check.
 *
 * <p>The cost is a pure domain value: it carries no currency and no economy provider. Whether a cost is
 * actually charged is decided in the application layer, which soft-couples to the economy context only when
 * a provider is present; with no provider the cost is recorded on the warp but ignored at use time. This
 * keeps warps free of any hard dependency on the economy context (which lands in P3).
 *
 * @param amount the price to use the warp; {@code BigDecimal.ZERO} for a free warp
 */
public record WarpCost(BigDecimal amount, String currencyId) {

    private static final WarpCost FREE = new WarpCost(BigDecimal.ZERO, "default");

    public WarpCost {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("warp cost must not be negative: " + amount);
        }
    }

    /** A concrete, non-negative price in default currency. */
    public static WarpCost of(BigDecimal amount) {
        return new WarpCost(amount, "default");
    }

    /** A concrete, non-negative price with a custom currency ID. */
    public static WarpCost of(BigDecimal amount, String currencyId) {
        return new WarpCost(amount, currencyId);
    }

    /** The "no charge" cost: a warp anyone may use without paying. */
    public static WarpCost free() {
        return FREE;
    }

    /** True when this cost charges nothing, so the economy gate can be skipped entirely. */
    public boolean isFree() {
        return amount.signum() == 0;
    }
}
