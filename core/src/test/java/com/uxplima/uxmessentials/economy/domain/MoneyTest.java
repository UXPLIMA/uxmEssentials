package com.uxplima.uxmessentials.economy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.economy.fakes.Currencies;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link Money} arithmetic, the value object every balance, port method, and domain event
 * carries. The load-bearing properties: arithmetic is exact (BigDecimal scaled to the currency precision,
 * never a {@code double}, so rounding drift is impossible), it is currency-bound (combining across currencies
 * throws rather than silently converting. The GLOSSARY invariant (b)), and the comparisons the clamp and the
 * {@code min-pay} gate rely on are correct at the boundary.
 */
class MoneyTest {

    @Test
    void plusAndMinusAreExactWithinACurrency() {
        Money sum = Money.of(Currencies.COINS, new BigDecimal("10.25"))
                .plus(Money.of(Currencies.COINS, new BigDecimal("5.50")));

        assertThat(sum.amount()).isEqualByComparingTo(new BigDecimal("15.75"));
        assertThat(sum.minus(Money.of(Currencies.COINS, new BigDecimal("0.75"))).amount())
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void amountIsNormalisedToTheCurrencyPrecision() {
        // COINS has precision 2; a finer input is half-up rounded to the stored scale, never carried as extra
        // digits the guarded UPDATE would then mis-compare.
        Money rounded = Money.of(Currencies.COINS, new BigDecimal("1.005"));

        assertThat(rounded.amount()).isEqualByComparingTo(new BigDecimal("1.01"));
        assertThat(rounded.amount().scale()).isEqualTo(2);
    }

    @Test
    void combiningAcrossCurrenciesThrows() {
        Money coins = Money.of(Currencies.COINS, 10);
        Money gems = Money.of(Currencies.GEMS, 10);

        assertThatThrownBy(() -> coins.plus(gems))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("across currencies");
        assertThatThrownBy(() -> coins.minus(gems)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coins.isLessThan(gems)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparisonsAreCurrencyScoped() {
        Money ten = Money.of(Currencies.COINS, 10);
        Money twenty = Money.of(Currencies.COINS, 20);

        assertThat(ten.isLessThan(twenty)).isTrue();
        assertThat(twenty.isGreaterThan(ten)).isTrue();
        assertThat(ten.isLessThan(ten)).isFalse();
    }

    @Test
    void zeroAndNegativeAreReportedForTheClampAndMinPayGates() {
        assertThat(Money.zero(Currencies.COINS).isZero()).isTrue();
        assertThat(Money.of(Currencies.COINS, 5).isZero()).isFalse();
        assertThat(Money.of(Currencies.COINS, new BigDecimal("-1")).isNegative())
                .isTrue();
        assertThat(Money.of(Currencies.COINS, 1).isNegative()).isFalse();
    }

    @Test
    void equalityIsByValue() {
        assertThat(Money.of(Currencies.COINS, 10)).isEqualTo(Money.of(Currencies.COINS, new BigDecimal("10.00")));
        assertThat(Money.of(Currencies.COINS, 10)).isNotEqualTo(Money.of(Currencies.GEMS, 10));
    }
}
