package com.uxplima.uxmessentials.economy.application;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The operator-configured per-item sell value: a case-insensitive material-id → unit-price map, loaded once
 * from the economy config (no DB, no migration: a worth is a pricing policy, not persisted player state).
 * The price is per single item in the economy's default currency, so a stack value is the unit price times
 * the amount. A material absent from the table has no worth and is "not sellable". The lookup yields an empty
 * {@link Optional} rather than defaulting to zero, so {@code /worth} and {@code /sell} can tell the two apart.
 *
 * <p>This is the config-backed implementation of {@link WorthSource}; the {@code /setworth} override store is
 * the other, and {@link CombiningWorthSource} consults the store first then falls back to this table.
 */
public final class WorthTable implements WorthSource {

    private final Map<String, Worth> prices;

    public WorthTable(Map<String, Worth> prices) {
        Objects.requireNonNull(prices, "prices");
        Map<String, Worth> normalized = new HashMap<>();
        prices.forEach((material, worth) -> normalized.put(
                Objects.requireNonNull(material, "material").toLowerCase(Locale.ROOT),
                Objects.requireNonNull(worth, "worth")));
        this.prices = Map.copyOf(normalized);
    }

    /** An empty table: every material is unpriced. */
    public static WorthTable empty() {
        return new WorthTable(Map.of());
    }

    /** The configured unit price for {@code material}, or empty when the material has no worth. */
    @Override
    public Optional<Worth> unitPrice(String material) {
        Objects.requireNonNull(material, "material");
        return Optional.ofNullable(prices.get(material.toLowerCase(Locale.ROOT)));
    }
}
