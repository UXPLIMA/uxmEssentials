package com.uxplima.uxmessentials.economy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * The exchange-rate value object's target computation. A normal conversion yields the rate-and-fee-adjusted
 * amount; a source amount so small it rounds to zero at the target precision yields no amount at all, so the
 * service can refuse the move rather than debit the source for nothing.
 */
class ExchangeRateTest {

    private static final CurrencyId SOURCE = CurrencyId.of("coins");
    private static final CurrencyId TARGET = CurrencyId.of("gems");

    @Test
    void aNormalConversionAppliesTheRateAndFee() {
        ExchangeRate rate = new ExchangeRate(SOURCE, TARGET, new BigDecimal("2"), new BigDecimal("0.10"));

        // 100 * 2 = 200 gross, less 10% fee = 180.
        assertThat(rate.calculateTargetAmount(new BigDecimal("100"), 2)).contains(new BigDecimal("180.00"));
    }

    @Test
    void aTinySourceThatRoundsToZeroYieldsNoAmount() {
        // 0.001 source at a rate of 1 and precision 2 rounds to 0.00: a move that would debit for nothing.
        ExchangeRate rate = new ExchangeRate(SOURCE, TARGET, BigDecimal.ONE, BigDecimal.ZERO);

        assertThat(rate.calculateTargetAmount(new BigDecimal("0.001"), 2)).isEmpty();
    }

    @Test
    void aFeeThatWipesTheGrossDownToZeroYieldsNoAmount() {
        // 1 * 1 = 1 gross, precision 0, but a 99% fee leaves 0.01 which rounds to 0 at precision 0.
        ExchangeRate rate = new ExchangeRate(SOURCE, TARGET, BigDecimal.ONE, new BigDecimal("0.99"));

        assertThat(rate.calculateTargetAmount(BigDecimal.ONE, 0)).isEmpty();
    }
}
