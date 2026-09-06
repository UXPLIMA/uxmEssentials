package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;

/**
 * Periodic interest paid into a shared bank, loaded once on enable and swapped atomically on reload (the
 * {@code LoanPolicy} pattern). Interest is balance-tiered: each {@link Tier} names a minimum balance and the rate
 * that applies at or above it, so a bigger bank earns a better rate. {@link #interestOn} resolves the highest tier
 * a balance qualifies for and returns the cut to mint into the bank. Never exceeding the balance's currency, and
 * zero when disabled, when no tier applies, or when the rate rounds to nothing.
 *
 * @param enabled whether interest accrues at all
 * @param interval how often interest is paid
 * @param tiers the rate bands, kept sorted ascending by {@code minBalance}
 */
public record BankInterestPolicy(boolean enabled, Duration interval, List<Tier> tiers) {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /** One rate band: at or above {@code minBalance}, interest is {@code ratePercent}% of the balance. */
    public record Tier(BigDecimal minBalance, BigDecimal ratePercent) {
        public Tier {
            Objects.requireNonNull(minBalance, "minBalance");
            Objects.requireNonNull(ratePercent, "ratePercent");
        }
    }

    public BankInterestPolicy {
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(tiers, "tiers");
        List<Tier> sorted = new ArrayList<>(tiers);
        sorted.sort(Comparator.comparing(Tier::minBalance));
        tiers = List.copyOf(sorted);
    }

    /** A disabled policy: the shipped default, accruing no interest. */
    public static BankInterestPolicy disabled() {
        return new BankInterestPolicy(false, Duration.ofHours(24), List.of());
    }

    /** The interest to mint into a bank holding {@code balance}; zero when disabled or no tier applies. */
    public Money interestOn(Money balance) {
        Objects.requireNonNull(balance, "balance");
        if (!enabled) {
            return Money.zero(balance.currency());
        }
        BigDecimal rate = rateFor(balance.amount());
        if (rate.signum() <= 0) {
            return Money.zero(balance.currency());
        }
        int scale = balance.currency().precision() + 4;
        BigDecimal interest = balance.amount().multiply(rate).divide(ONE_HUNDRED, scale, RoundingMode.HALF_UP);
        if (interest.signum() <= 0) {
            return Money.zero(balance.currency());
        }
        return Money.of(balance.currency(), interest);
    }

    /** The rate of the highest tier whose minimum {@code amount} meets, or zero when none applies. */
    private BigDecimal rateFor(BigDecimal amount) {
        BigDecimal rate = BigDecimal.ZERO;
        for (Tier tier : tiers) {
            if (amount.compareTo(tier.minBalance()) >= 0) {
                rate = tier.ratePercent();
            }
        }
        return rate;
    }
}
