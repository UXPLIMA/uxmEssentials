package com.uxplima.uxmessentials.economy.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An exact amount paired with its {@link Currency}. Value object (equality by value, immutable) backed by
 * a {@link BigDecimal} scaled to the currency's precision so currency arithmetic never picks up the binary
 * floating-point error a {@code double} would carry; rounding drift is a money bug, not a rounding nicety
 * (the economy GLOSSARY, invariant (b)).
 *
 * <p>{@code Money} is <strong>currency-bound</strong>: {@link #plus} and {@link #minus} are defined within
 * one currency only and mixing currencies throws {@link IllegalArgumentException}. Every {@link Wallet}
 * balance entry, every {@code EconomyProvider} method, and every domain event carries a {@code Money} whose
 * currency is the unit the operation is scoped to. The raw {@link #amount()} is what the persistence layer
 * stores in the {@code economy_wallet.balance} column and what the guarded debit {@code UPDATE} compares.
 *
 * @param currency the unit this amount is denominated in
 * @param amount the exact figure, scaled to {@link Currency#precision()}
 */
public record Money(Currency currency, BigDecimal amount) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        amount = currency.normalize(amount);
    }

    /** An amount in {@code currency}, normalised to its precision. */
    public static Money of(Currency currency, BigDecimal amount) {
        return new Money(currency, amount);
    }

    /** An amount in {@code currency} from a whole-number quantity, normalised to its precision. */
    public static Money of(Currency currency, long amount) {
        return new Money(currency, BigDecimal.valueOf(amount));
    }

    /** The zero figure for {@code currency}: what an untouched balance projects to. */
    public static Money zero(Currency currency) {
        return new Money(currency, BigDecimal.ZERO);
    }

    /**
     * This amount plus {@code other}, within the same currency. Adding across currencies is a modelling
     * error, never a silent conversion (invariant (b)), so a mismatch throws.
     */
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(currency, amount.add(other.amount));
    }

    /** This amount minus {@code other}, within the same currency; a currency mismatch throws. */
    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(currency, amount.subtract(other.amount));
    }

    /** True when this amount is strictly less than {@code other} (same currency); a mismatch throws. */
    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    /** True when this amount is strictly greater than {@code other} (same currency); a mismatch throws. */
    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    /** True when this amount is zero. */
    public boolean isZero() {
        return amount.signum() == 0;
    }

    /** True when this amount is negative, used by the clamp and the {@code min-pay} validation. */
    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "cannot combine money across currencies: " + currency + " and " + other.currency);
        }
    }
}
