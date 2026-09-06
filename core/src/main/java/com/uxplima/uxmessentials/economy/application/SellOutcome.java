package com.uxplima.uxmessentials.economy.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.Money;

/**
 * The result of a {@code /sell}: whether items were sold and, when they were, how much was earned. A refusal
 * (nothing in hand, an unpriced material, or a credit the currency clamp rejected) carries {@code sold=false}
 * and no proceeds; the use case has already told the seller why through the {@link EconomyNotifier}. The
 * adapter reads {@link #sold()} to decide whether to remove the items from the seller's inventory.
 *
 * @param sold true when the credit applied and the items should be removed
 * @param earned the proceeds when sold, otherwise empty
 */
public record SellOutcome(boolean sold, Optional<Money> earned) {

    public SellOutcome {
        Objects.requireNonNull(earned, "earned");
        if (sold && earned.isEmpty()) {
            throw new IllegalArgumentException("a completed sale must carry its proceeds");
        }
        if (!sold && earned.isPresent()) {
            throw new IllegalArgumentException("a refused sale carries no proceeds");
        }
    }

    /** A completed sale that earned {@code amount}. */
    public static SellOutcome sold(Money amount) {
        return new SellOutcome(true, Optional.of(Objects.requireNonNull(amount, "amount")));
    }

    /** A refused sale: nothing was credited and nothing should be removed. */
    public static SellOutcome refused() {
        return new SellOutcome(false, Optional.empty());
    }
}
