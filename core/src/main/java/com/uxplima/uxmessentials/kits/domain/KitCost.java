package com.uxplima.uxmessentials.kits.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The optional price of claiming a kit, modelled as an exact {@link BigDecimal} so currency amounts never
 * pick up binary floating-point error. A kit without a cost is represented by {@link #free()} rather than a
 * {@code null} amount, so the "no charge" case is a first-class value the claim use case can branch on
 * without a null check.
 *
 * <p>The cost is a pure domain value: it carries no currency and no economy provider. Whether a cost is
 * actually charged is decided in the application layer, which soft-couples to the economy context only when
 * a provider is present; with no provider the cost is recorded on the kit but ignored at claim time. This
 * keeps kits free of any hard dependency on the economy context.
 *
 * @param amount the price to claim the kit; {@code BigDecimal.ZERO} for a free kit
 */
public record KitCost(BigDecimal amount, String currencyId) {

    private static final KitCost FREE = new KitCost(BigDecimal.ZERO, "default");

    public KitCost {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("kit cost must not be negative: " + amount);
        }
    }

    /** A concrete, non-negative price in default currency. */
    public static KitCost of(BigDecimal amount) {
        return new KitCost(amount, "default");
    }

    /** A concrete, non-negative price with a custom currency ID. */
    public static KitCost of(BigDecimal amount, String currencyId) {
        return new KitCost(amount, currencyId);
    }

    /** The "no charge" cost: a kit anyone may claim without paying. */
    public static KitCost free() {
        return FREE;
    }

    /** True when this cost charges nothing, so the economy gate can be skipped entirely. */
    public boolean isFree() {
        return amount.signum() == 0;
    }
}
