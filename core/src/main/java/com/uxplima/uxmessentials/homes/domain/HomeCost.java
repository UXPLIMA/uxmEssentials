package com.uxplima.uxmessentials.homes.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The optional price of a home action (create, relocate, or teleport), modelled as an exact
 * {@link BigDecimal} so currency amounts never pick up binary floating-point error. A free action is
 * represented by {@link #free()} rather than a {@code null} amount, so the "no charge" case is a
 * first-class value the application layer can branch on without a null check.
 *
 * <p>The cost is a pure domain value: it carries a currency ID but no economy provider. Whether a cost
 * is actually charged is decided in the application layer, which soft-couples to the economy context only
 * when a provider is present; with no provider the cost is recorded in config but ignored at use time.
 * This keeps homes free of any hard dependency on the economy context.
 *
 * @param amount the price of the action; {@code BigDecimal.ZERO} for a free action
 * @param currencyId the currency to charge; {@code "default"} when the server's primary currency applies
 */
public record HomeCost(BigDecimal amount, String currencyId) {

    private static final HomeCost FREE = new HomeCost(BigDecimal.ZERO, "default");

    public HomeCost {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("home cost must not be negative: " + amount);
        }
    }

    /** A concrete, non-negative price in the server's default currency. */
    public static HomeCost of(BigDecimal amount) {
        return new HomeCost(amount, "default");
    }

    /** A concrete, non-negative price with a custom currency ID. */
    public static HomeCost of(BigDecimal amount, String currencyId) {
        return new HomeCost(amount, currencyId);
    }

    /** The "no charge" cost: an action anyone may perform without paying. */
    public static HomeCost free() {
        return FREE;
    }

    /** True when this cost charges nothing, so the economy gate can be skipped entirely. */
    public boolean isFree() {
        return amount.signum() == 0;
    }
}
