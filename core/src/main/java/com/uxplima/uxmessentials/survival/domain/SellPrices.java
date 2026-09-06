package com.uxplima.uxmessentials.survival.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The autosell price table: the per-item price each sellable material fetches when autosell credits a break's drops to
 * the player's wallet. It is the pure lookup behind "sell drops to the economy at configured prices" (modelled on
 * AdvancedAutoSmelt's {@code sell_prices.yml}). The adapter iterates the computed drops and credits the wallet, but
 * the price maths carries no economy or Bukkit identity, so it is unit-testable on plain strings and {@link BigDecimal}.
 *
 * <p>Prices are stored per single item; {@link #saleValue(String, int)} multiplies by the stack amount so a stack of
 * sixty-four sells for sixty-four times its unit price. A material absent from the table is not sellable and its drop
 * simply falls through to autopickup or the ground: autosell never destroys an item it cannot price.
 *
 * @param prices the material name → per-item price pairs (each price finite and non-negative)
 */
public record SellPrices(Map<String, BigDecimal> prices) {

    public SellPrices {
        Objects.requireNonNull(prices, "prices");
        Map<String, BigDecimal> normalised = new LinkedHashMap<>();
        prices.forEach((material, price) -> {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(price, "price");
            if (price.signum() < 0) {
                throw new IllegalArgumentException("sell price must be non-negative: " + material + " = " + price);
            }
            normalised.put(material.toUpperCase(Locale.ROOT), price);
        });
        prices = Map.copyOf(normalised);
    }

    /**
     * The per-item price of {@code material}, or empty when it is not sellable.
     *
     * @param material the drop material name (case-insensitive, e.g. {@code DIAMOND})
     * @return the unit price, or empty when the material has no configured price
     */
    public Optional<BigDecimal> priceOf(String material) {
        Objects.requireNonNull(material, "material");
        return Optional.ofNullable(prices.get(material.toUpperCase(Locale.ROOT)));
    }

    /**
     * The total sale value of {@code amount} of {@code material}, or empty when the material is not sellable.
     *
     * @param material the drop material name (case-insensitive)
     * @param amount the stack size being sold, at least one
     * @return the unit price times {@code amount}, or empty when the material has no configured price
     */
    public Optional<BigDecimal> saleValue(String material, int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least one: " + amount);
        }
        return priceOf(material).map(unit -> unit.multiply(BigDecimal.valueOf(amount)));
    }

    /** Whether the table carries no prices, so autosell has nothing it can sell. */
    public boolean isEmpty() {
        return prices.isEmpty();
    }
}
