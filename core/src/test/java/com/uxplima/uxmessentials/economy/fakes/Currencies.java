package com.uxplima.uxmessentials.economy.fakes;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;

/**
 * The fixed test currencies the economy tests use. {@code coins} (the default) and {@code gems} (a second
 * currency proving the multi-currency path). The worked example exercises the single-currency-default path
 * with {@code coins}; {@code gems} is here so a test can assert crediting one currency leaves the other
 * untouched and that arithmetic across the two throws.
 */
public final class Currencies {

    /** The default ship-out-of-the-box currency, precision 2, no confirm threshold by default. */
    public static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    /** A second configured currency, proving the per-currency balance map. */
    public static final Currency GEMS = Currency.builder(CurrencyId.of("gems"))
            .symbol("♦")
            .plural("gems")
            .precision(0)
            .build();

    private Currencies() {}

    /** {@code coins} with a {@code max-balance} cap, for the over-max rejection test. */
    public static Currency cappedCoins(long max) {
        return Currency.builder(CurrencyId.of("coins"))
                .symbol("$")
                .plural("coins")
                .precision(2)
                .max(BigDecimal.valueOf(max))
                .build();
    }

    /** {@code coins} with a confirm threshold, for the {@code /payconfirm} routing test. */
    public static Currency confirmCoins(long threshold) {
        return Currency.builder(CurrencyId.of("coins"))
                .symbol("$")
                .plural("coins")
                .precision(2)
                .confirmThreshold(BigDecimal.valueOf(threshold))
                .build();
    }
}
