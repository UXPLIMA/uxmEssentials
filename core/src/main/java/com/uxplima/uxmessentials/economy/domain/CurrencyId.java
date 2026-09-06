package com.uxplima.uxmessentials.economy.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The stable identifier of a {@link Currency}. The {@code <id>} under {@code economy.currencies.<id>}
 * an operator configures, normalised to its canonical lowercase form so {@code COINS} and {@code coins}
 * name the same currency. It is the currency's identity across config, the persisted {@code (uuid,
 * currency)} key, the optional {@code [currency]} command argument, and the audit {@code currency} field;
 * the human-facing symbol and plural live on the {@link Currency} value object, not here.
 *
 * <p>The shape is constrained to the lowercase letters/digits/hyphen a config key and a database column
 * tolerate, so an id that reaches the aggregate or the {@code economy_wallet.currency} column is always a
 * value the persistence key can carry without escaping.
 *
 * @param value the canonical lowercase identifier
 */
public record CurrencyId(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9-]*");

    /** Mirrors the {@code economy_wallet.currency} column width in the economy migration baseline. */
    public static final int MAX_LENGTH = 32;

    public CurrencyId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "currency id must be lowercase letters/digits/hyphen, starting with a letter: " + value);
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("currency id must be at most " + MAX_LENGTH + " characters: " + value);
        }
    }

    /** Build an id from raw config/command input, trimming and lower-casing it. */
    public static CurrencyId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new CurrencyId(raw.strip().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
