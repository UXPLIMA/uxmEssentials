package com.uxplima.uxmessentials.economy.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;

/**
 * The result of a {@code /sellall}: which materials were sold and in what quantity, plus the proceeds credited
 * per currency. A refusal. An empty inventory, nothing priced, or every currency's credit rejected by the
 * clamp, carries an empty {@link #sold()} map and no {@link #proceeds()}; the use case has already told the
 * seller why through the {@link EconomyNotifier}. The adapter reads {@link #sold()} to decide which stacks to
 * remove from the seller's inventory, so the inventory and the balance never diverge. A material appears in
 * {@link #sold()} only once the credit for its currency actually applied.
 *
 * @param sold the material id → quantity actually sold (empty on a refusal)
 * @param proceeds the per-currency proceeds credited (empty on a refusal)
 */
public record SellAllOutcome(Map<String, Integer> sold, Map<Currency, Money> proceeds) {

    public SellAllOutcome {
        Objects.requireNonNull(sold, "sold");
        Objects.requireNonNull(proceeds, "proceeds");
        sold = Map.copyOf(sold);
        proceeds = Map.copyOf(proceeds);
        if (sold.isEmpty() != proceeds.isEmpty()) {
            throw new IllegalArgumentException("a completed sale carries both its items and its proceeds");
        }
    }

    /** A completed sale of {@code sold} crediting {@code proceeds} per currency. */
    public static SellAllOutcome sold(Map<String, Integer> sold, Map<Currency, Money> proceeds) {
        return new SellAllOutcome(sold, proceeds);
    }

    /** A refused sale: nothing was credited and nothing should be removed. */
    public static SellAllOutcome refused() {
        return new SellAllOutcome(Map.of(), Map.of());
    }

    /**
     * The proceeds collapsed to a single currency when the whole sale paid out in one, the common
     * single-currency case. Empty when the sale was refused or paid out in more than one currency, where the
     * caller must read {@link #proceeds()} to see every total.
     */
    public java.util.Optional<Money> earned() {
        if (proceeds.size() != 1) {
            return java.util.Optional.empty();
        }
        return proceeds.values().stream().findFirst();
    }

    /** The proceeds as a stable map view, ordered as they were credited. */
    public Map<Currency, Money> perCurrency() {
        return new LinkedHashMap<>(proceeds);
    }
}
