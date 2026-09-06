package com.uxplima.uxmessentials.economy.application;

import java.util.Locale;

/**
 * How {@link MoneyFormat} renders an amount into the value a {@link EconomyMessageKey} interpolates: {@link
 * #FULL} keeps every digit with the currency's grouping pattern ({@code 1,234,500.00}), while {@link #COMPACT}
 * abbreviates large figures with a magnitude suffix ({@code 1.23M}). This is a <em>display</em> choice only
 * the stored balance is untouched either way, selected by the operator through {@code economy.amount-format =
 * full|compact}, defaulting to {@link #FULL} so the shipped behaviour is unchanged.
 */
public enum AmountFormat {

    /** Every digit, grouped by the currency pattern: the default, the pre-existing behaviour. */
    FULL,

    /** Large figures abbreviated with a {@code K}/{@code M}/{@code B}/{@code T} suffix. */
    COMPACT;

    /** Parse the {@code amount-format} config value, falling back to {@link #FULL} for anything unknown. */
    public static AmountFormat fromConfig(String raw) {
        return COMPACT.token().equalsIgnoreCase(raw.strip()) ? COMPACT : FULL;
    }

    /** The lowercase token an operator writes in config: the inverse of {@link #fromConfig(String)}. */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }
}
