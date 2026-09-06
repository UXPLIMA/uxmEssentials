package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;

/**
 * Renders a {@link Money} amount into the placeholder string a {@link EconomyMessageKey} interpolates, the
 * numeric value formatted to the currency's {@code format} pattern and tagged with its symbol. This is the
 * <em>value</em> a {@code MessageKey} carries, never a standalone user-facing message, so it stays clear of
 * the inline-literal and {@code String.format}-for-chat rules: the surrounding sentence is the catalog
 * string; this is the amount slotted into its {@code {amount}} placeholder.
 *
 * <p>Formatting is locale-stable on purpose. Economy amounts use {@link Locale#ROOT} grouping so a
 * balance reads identically regardless of the viewer's locale, while the catalog sentence around it is
 * localized as usual. The currency's configured {@code format} pattern drives the digit grouping and the
 * decimal places. An {@link AmountFormat} selects between the full grouped figure and a compact abbreviated
 * one ({@code 1.23M}); the no-{@code AmountFormat} overloads keep the pre-existing full rendering.
 */
public final class MoneyFormat {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};

    private MoneyFormat() {}

    /** The amount of {@code money} formatted by its currency pattern, e.g. {@code 1,234.50}. */
    public static String amount(Money money) {
        return amount(money, AmountFormat.FULL);
    }

    /** The amount with the currency symbol applied, e.g. {@code $1,234.50}. */
    public static String withSymbol(Money money) {
        return withSymbol(money, AmountFormat.FULL);
    }

    /** The amount of {@code money} rendered in {@code style}, e.g. {@code 1,234.50} or {@code 1.23K}. */
    public static String amount(Money money, AmountFormat style) {
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(style, "style");
        return style == AmountFormat.COMPACT ? compact(money) : full(money);
    }

    /** The amount with the currency symbol applied, rendered in {@code style}. */
    public static String withSymbol(Money money, AmountFormat style) {
        return Objects.requireNonNull(money, "money").currency().symbol() + amount(money, style);
    }

    private static String full(Money money) {
        Currency currency = money.currency();
        DecimalFormat format = new DecimalFormat(currency.format(), DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setParseBigDecimal(true);
        return format.format(money.amount());
    }

    private static String compact(Money money) {
        BigDecimal magnitude = money.amount().abs();
        int tier = 0;
        BigDecimal scaled = magnitude;
        while (tier < SUFFIXES.length - 1 && scaled.compareTo(THOUSAND) >= 0) {
            scaled = scaled.divide(THOUSAND);
            tier++;
        }
        String sign = money.amount().signum() < 0 ? "-" : "";
        return sign + trim(scaled) + SUFFIXES[tier];
    }

    private static String trim(BigDecimal scaled) {
        BigDecimal rounded = scaled.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.scale() <= 0 ? rounded.toBigInteger().toString() : rounded.toPlainString();
    }
}
