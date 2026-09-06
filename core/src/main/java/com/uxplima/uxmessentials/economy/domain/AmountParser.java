package com.uxplima.uxmessentials.economy.domain;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * Parses a human-typed amount token into an exact {@link Money} in a given {@link Currency}. Accepts the
 * shorthand operators reach for at the command line, a magnitude suffix ({@code k}=10^3, {@code m}=10^6,
 * {@code b}=10^9, {@code t}=10^12, {@code q}=10^15, case-insensitive) and thousands separators ({@code 1,000}
 * or {@code 1.000})
 *, while staying {@link BigDecimal}-exact throughout: the suffix multiply is a {@code BigDecimal} scale, never
 * a {@code double}, so {@code 1.5m} is exactly {@code 1500000} and rounding drift stays impossible (the economy
 * GLOSSARY invariant (b)).
 *
 * <p>Separator handling is locale-agnostic and deterministic. With both {@code ,} and {@code .} present the
 * rightmost is the decimal point and the other is grouping ({@code 1.000,50} and {@code 1,000.50} both parse to
 * {@code 1000.50}). A single separator reads as grouping only when it cleanly delimits thousands groups, a
 * 1-3 digit lead followed by groups of exactly three ({@code 1,000} -> {@code 1000}, {@code 1.234.567} ->
 * {@code 1234567}); otherwise it is a decimal point ({@code 1.5} -> {@code 1.5}, {@code 1,5} -> {@code 1.5},
 * {@code 1.2345} -> {@code 1.2345}). A malformed grouping like {@code 1.2.3} is rejected rather than silently
 * concatenated. The parsed figure is normalised to the currency precision and clamped into {@code [min, max]}
 * so an overflowing {@code 9999t} settles at the currency maximum rather than rejecting.
 *
 * <p>Pure and side-effect-free: it returns a {@link Result} carrying either the {@link Money} or an
 * {@link AmountParseError}, never throwing for bad input. A zero or negative figure is {@link
 * AmountParseError#NOT_POSITIVE}; anything unrecognisable is {@link AmountParseError#NOT_A_NUMBER}.
 */
public final class AmountParser {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal BILLION = new BigDecimal("1000000000");
    private static final BigDecimal TRILLION = new BigDecimal("1000000000000");
    private static final BigDecimal QUADRILLION = new BigDecimal("1000000000000000");

    private AmountParser() {}

    /**
     * Parse {@code raw} into a strictly positive {@link Money} in {@code currency}, clamped to the currency's
     * configured {@code [min, max]} range.
     */
    public static Result<Money, AmountParseError> parse(String raw, Currency currency) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(currency, "currency");
        BigDecimal magnitude = figure(raw.strip());
        if (magnitude == null) {
            return Result.err(AmountParseError.NOT_A_NUMBER);
        }
        if (magnitude.signum() <= 0) {
            return Result.err(AmountParseError.NOT_POSITIVE);
        }
        BigDecimal clamped = clamp(currency.normalize(magnitude), currency);
        if (clamped.signum() <= 0) {
            return Result.err(AmountParseError.NOT_POSITIVE);
        }
        return Result.ok(Money.of(currency, clamped));
    }

    private static @org.jspecify.annotations.Nullable BigDecimal figure(String token) {
        if (token.isEmpty()) {
            return null;
        }
        BigDecimal multiplier = suffixMultiplier(token);
        String digits = multiplier == null ? token : token.substring(0, token.length() - 1);
        String normalised = normaliseSeparators(digits);
        BigDecimal base = normalised == null ? null : decimal(normalised);
        if (base == null) {
            return null;
        }
        return multiplier == null ? base : base.multiply(multiplier);
    }

    private static @org.jspecify.annotations.Nullable BigDecimal suffixMultiplier(String token) {
        return switch (Character.toLowerCase(token.charAt(token.length() - 1))) {
            case 'k' -> THOUSAND;
            case 'm' -> MILLION;
            case 'b' -> BILLION;
            case 't' -> TRILLION;
            case 'q' -> QUADRILLION;
            default -> null;
        };
    }

    private static @org.jspecify.annotations.Nullable String normaliseSeparators(String digits) {
        int lastComma = digits.lastIndexOf(',');
        int lastDot = digits.lastIndexOf('.');
        if (lastComma < 0 && lastDot < 0) {
            return digits;
        }
        if (lastComma >= 0 && lastDot >= 0) {
            char decimal = lastComma > lastDot ? ',' : '.';
            return stripGrouping(digits, decimal);
        }
        return resolveSingleSeparator(digits, lastComma >= 0 ? ',' : '.');
    }

    private static @org.jspecify.annotations.Nullable String resolveSingleSeparator(String digits, char separator) {
        int last = digits.lastIndexOf(separator);
        boolean repeated = digits.indexOf(separator) != last;
        boolean trailingTriple = digits.length() - last - 1 == 3;
        if (repeated || trailingTriple) {
            return isGrouped(digits, separator) ? digits.replace(String.valueOf(separator), "") : null;
        }
        return digits.replace(separator, '.');
    }

    private static @org.jspecify.annotations.Nullable String stripGrouping(String digits, char decimal) {
        char grouping = decimal == ',' ? '.' : ',';
        String integerPart = digits.substring(0, digits.lastIndexOf(decimal));
        if (!isGrouped(integerPart, grouping)) {
            return null;
        }
        return digits.replace(String.valueOf(grouping), "").replace(decimal, '.');
    }

    /**
     * True when {@code separator} delimits the digit string into thousands groups: a 1-3 digit lead followed
     * by one or more groups of exactly three digits ({@code 1.234.567}). A malformed grouping ({@code 1.2.3})
     * is not, so it is rejected rather than silently concatenated.
     */
    private static boolean isGrouped(String digits, char separator) {
        String[] groups = digits.split(java.util.regex.Pattern.quote(String.valueOf(separator)), -1);
        if (groups.length < 2 || groups[0].isEmpty() || groups[0].length() > 3) {
            return false;
        }
        for (int i = 1; i < groups.length; i++) {
            if (groups[i].length() != 3) {
                return false;
            }
        }
        return true;
    }

    private static @org.jspecify.annotations.Nullable BigDecimal decimal(String value) {
        if (value.isEmpty() || ".".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static BigDecimal clamp(BigDecimal amount, Currency currency) {
        if (amount.compareTo(currency.max()) > 0) {
            return currency.max();
        }
        if (amount.compareTo(currency.min()) < 0) {
            return currency.min();
        }
        return amount;
    }
}
