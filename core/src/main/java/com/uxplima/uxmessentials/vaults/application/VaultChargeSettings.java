package com.uxplima.uxmessentials.vaults.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The per-action cost configuration for the vaults context, bundled into one record so the config layer
 * constructs it once on enable (swapped atomically on reload) and {@link VaultCharge} reads it without
 * spreading cost-lookup logic across use cases. Mirrors the homes {@code HomeChargeSettings}.
 *
 * <p>Amounts are exact {@link BigDecimal} values so currency arithmetic never picks up binary
 * floating-point error, matching the economy ledger invariant. A non-positive amount means "free" for that
 * action: {@link VaultCharge} skips the economy entirely when the relevant cost is zero.
 *
 * @param costToCreate charged once when a not-yet-existing vault index is first allocated
 * @param costToOpen charged each time a vault is opened (a per-open fee; zero disables it)
 * @param refundOnDelete paid back to the owner when their vault is deleted (zero disables the refund)
 */
public record VaultChargeSettings(BigDecimal costToCreate, BigDecimal costToOpen, BigDecimal refundOnDelete) {

    public VaultChargeSettings {
        Objects.requireNonNull(costToCreate, "costToCreate");
        Objects.requireNonNull(costToOpen, "costToOpen");
        Objects.requireNonNull(refundOnDelete, "refundOnDelete");
        requireNonNegative(costToCreate, "costToCreate");
        requireNonNegative(costToOpen, "costToOpen");
        requireNonNegative(refundOnDelete, "refundOnDelete");
    }

    /** A settings instance with every action free and no refund: used when economy is disabled or unwired. */
    public static VaultChargeSettings allFree() {
        return new VaultChargeSettings(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** A settings instance from plain amounts (the config layer's doubles), each clamped to non-negative. */
    public static VaultChargeSettings of(double costToCreate, double costToOpen, double refundOnDelete) {
        return new VaultChargeSettings(nonNegative(costToCreate), nonNegative(costToOpen), nonNegative(refundOnDelete));
    }

    private static BigDecimal nonNegative(double amount) {
        return amount <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(amount);
    }

    private static void requireNonNegative(BigDecimal amount, String field) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative: " + amount);
        }
    }
}
