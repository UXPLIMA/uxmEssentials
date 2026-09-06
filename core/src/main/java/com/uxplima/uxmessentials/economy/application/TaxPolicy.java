package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;

/**
 * The pay-tax rule, loaded once on enable and swapped atomically on reload (the {@code LoanPolicy} pattern). A
 * {@code /pay} of a gross amount is taxed by {@code percent}% of the amount plus a {@code flat} cut; the result
 * is the money the server takes out of the transfer (the receiver is credited the remainder). The tax is a sink
 * that fights inflation. It is computed here, in the currency of the payment, and never exceeds the payment
 * itself so a receiver can never be credited a negative figure.
 *
 * @param enabled whether any tax is applied at all (the default-off switch)
 * @param percent the percentage of the gross amount taken, {@code 0..100} (e.g. {@code 5} = 5%)
 * @param flat a flat cut added on top of the percentage, in the payment's currency units
 */
public record TaxPolicy(boolean enabled, BigDecimal percent, BigDecimal flat) {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public TaxPolicy {
        Objects.requireNonNull(percent, "percent");
        Objects.requireNonNull(flat, "flat");
        if (percent.signum() < 0) {
            throw new IllegalArgumentException("tax percent must not be negative: " + percent);
        }
        if (flat.signum() < 0) {
            throw new IllegalArgumentException("tax flat must not be negative: " + flat);
        }
    }

    /** A disabled policy: the shipped default, leaving every {@code /pay} untaxed. */
    public static TaxPolicy disabled() {
        return new TaxPolicy(false, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * The tax to take out of a {@code gross} payment, in {@code gross}'s currency. Zero when disabled or when
     * the computed cut rounds to nothing; capped at the gross so the receiver's remainder is never negative.
     */
    public Money tax(Money gross) {
        Objects.requireNonNull(gross, "gross");
        if (!enabled) {
            return Money.zero(gross.currency());
        }
        int scale = gross.currency().precision() + 4;
        BigDecimal fromPercent = gross.amount().multiply(percent).divide(ONE_HUNDRED, scale, RoundingMode.HALF_UP);
        BigDecimal raw = fromPercent.add(flat).min(gross.amount());
        if (raw.signum() <= 0) {
            return Money.zero(gross.currency());
        }
        return Money.of(gross.currency(), raw);
    }
}
