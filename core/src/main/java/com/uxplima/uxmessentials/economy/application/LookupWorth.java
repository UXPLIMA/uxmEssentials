package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /worth}: report the configured sell value of a material to a viewer, so a player can price loot
 * before committing to {@code /sell}. A pure pricing read against the {@link WorthSource}. It never touches a
 * balance. A single item renders the unit worth; a stack renders the unit worth and the stack total; a
 * material absent from the table renders the not-sellable notice. The amount is rendered through the
 * {@link EconomyNotifier} so the worth uses the same currency formatting as every other money figure.
 */
public final class LookupWorth {

    private final WorthSource worth;
    private final EconomyNotifier notifier;
    private final Currency defaultCurrency;
    private final java.util.Collection<Currency> currencies;

    public LookupWorth(
            WorthSource worth,
            EconomyNotifier notifier,
            Currency defaultCurrency,
            java.util.Collection<Currency> currencies) {
        this.worth = Objects.requireNonNull(worth, "worth");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.currencies = java.util.List.copyOf(currencies);
    }

    /** Report the worth of {@code amount} of {@code material} to {@code viewer}. */
    public void report(PlayerRef viewer, String material, int amount) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(material, "material");
        Optional<Worth> unit = worth.unitPrice(material);
        if (unit.isEmpty()) {
            notifier.send(viewer, EconomyMessageKey.WORTH_NOT_SELLABLE, Map.of("item", material));
            return;
        }
        Currency itemCurrency = currencies.stream()
                .filter(c -> c.id().value().equalsIgnoreCase(unit.get().currencyId()))
                .findFirst()
                .orElse(defaultCurrency);
        Money unitWorth = Money.of(itemCurrency, unit.get().amount());
        if (amount <= 1) {
            notifier.send(
                    viewer,
                    EconomyMessageKey.WORTH_RESULT,
                    Map.of("item", material, "amount", notifier.amount(unitWorth)));
            return;
        }
        Money stackWorth = Money.of(itemCurrency, unit.get().amount().multiply(BigDecimal.valueOf(amount)));
        notifier.send(
                viewer,
                EconomyMessageKey.WORTH_RESULT_STACK,
                Map.of(
                        "item", material,
                        "count", Integer.toString(amount),
                        "amount", notifier.amount(unitWorth),
                        "total", notifier.amount(stackWorth)));
    }
}
