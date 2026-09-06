package com.uxplima.uxmessentials.economy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Coverage of {@link AmountParser}, the human-amount parser behind {@code /pay} and {@code /eco}. The
 * load-bearing properties: each magnitude suffix scales by an exact {@link BigDecimal} power of ten (never a
 * {@code double}), thousands separators are stripped locale-agnostically, an unparseable or non-positive token
 * returns a modelled {@link AmountParseError} rather than throwing, and an overflowing figure clamps to the
 * currency maximum instead of rejecting.
 */
class AmountParserTest {

    @ParameterizedTest
    @CsvSource({
        "10k, 10000",
        "1.5m, 1500000",
        "2b, 2000000000",
        "1t, 1000000000000",
        "0.5k, 500",
        "10K, 10000",
        "1.5M, 1500000",
    })
    void suffixesScaleByExactPowersOfTen(String raw, String expected) {
        Result<Money, AmountParseError> result = AmountParser.parse(raw, Currencies.COINS);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().amount()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @CsvSource({
        "1000, 1000",
        "'1,000', 1000",
        "1.000, 1000",
        "'1,000.50', 1000.50",
        "'1.000,50', 1000.50",
        "'1,234,567', 1234567",
        "1.234.567, 1234567",
        "1.5, 1.5",
        "'1,5', 1.5",
    })
    void thousandsSeparatorsAreStrippedLocaleAgnostically(String raw, String expected) {
        Result<Money, AmountParseError> result = AmountParser.parse(raw, Currencies.COINS);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().amount()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @CsvSource({
        "1q, 1000000000000000",
        "2Q, 2000000000000000",
        "1.5q, 1500000000000000",
    })
    void quadrillionSuffixScalesByTenToTheFifteenth(String raw, String expected) {
        // A 1e15 figure overshoots the COINS 1e12 ceiling, so assert the multiplier against a currency whose
        // cap is high enough to see the unclamped value.
        Currency highCap = Currencies.cappedCoins(2_000_000_000_000_000L);

        Result<Money, AmountParseError> result = AmountParser.parse(raw, highCap);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().amount()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void plainDecimalIsNormalisedToTheCurrencyPrecision() {
        // A 4-digit fraction is unambiguously a decimal (not a thousands group), so it rounds half-up to the
        // currency's 2-place precision.
        Result<Money, AmountParseError> result = AmountParser.parse("1.2345", Currencies.COINS);

        assertThat(result.orElseThrow().amount()).isEqualByComparingTo(new BigDecimal("1.23"));
        assertThat(result.orElseThrow().amount().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "  ", "1.2.3", "10x", "k", "$5", "10 k", ".", "1,2,3.4.5"})
    void unparseableTokensAreNotANumber(String raw) {
        Result<Money, AmountParseError> result = AmountParser.parse(raw, Currencies.COINS);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(AmountParseError.NOT_A_NUMBER);
        assertThat(result.errorOrThrow().messageKey()).isEqualTo(EconomyMessageKey.PAY_INVALID_AMOUNT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-5k", "0.00", "-0.5m"})
    void nonPositiveFiguresAreRejected(String raw) {
        Result<Money, AmountParseError> result = AmountParser.parse(raw, Currencies.COINS);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(AmountParseError.NOT_POSITIVE);
        assertThat(result.errorOrThrow().messageKey()).isEqualTo(EconomyMessageKey.PAY_INVALID_AMOUNT);
    }

    @Test
    void overflowClampsToTheCurrencyMaximum() {
        // COINS defaults to a 1e12 ceiling; a figure beyond it settles at the maximum rather than rejecting.
        Result<Money, AmountParseError> result = AmountParser.parse("9999t", Currencies.COINS);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().amount()).isEqualByComparingTo(Currencies.COINS.max());
    }

    @Test
    void cappedCurrencyClampsToItsConfiguredMaximum() {
        Currency capped = Currencies.cappedCoins(500);

        Result<Money, AmountParseError> result = AmountParser.parse("10k", capped);

        assertThat(result.orElseThrow().amount()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void zeroPrecisionCurrencyRoundsToWholeUnits() {
        Result<Money, AmountParseError> result = AmountParser.parse("1.5k", Currencies.GEMS);

        assertThat(result.orElseThrow().amount()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.orElseThrow().amount().scale()).isEqualTo(0);
    }
}
