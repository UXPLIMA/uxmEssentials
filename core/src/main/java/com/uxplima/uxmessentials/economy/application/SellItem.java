package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /sell}: convert a counted material into currency at its configured {@link WorthSource} worth and
 * credit the seller through the {@link EconomyProvider}. Never a PDC stamp, so the proceeds survive a world
 * rollback like every other balance (the economy hard invariant). Returns a {@link SellOutcome} so the
 * adapter knows whether to remove the items from the inventory: only a credit that actually applied is
 * {@code sold}. An unpriced material, an empty hand, and a clamp rejection are each refused with the matching
 * notice and leave the inventory untouched.
 */
public final class SellItem {

    private final EconomyProvider economy;
    private final WorthSource worth;
    private final EconomyNotifier notifier;
    private final Currency defaultCurrency;
    private final java.util.Collection<Currency> currencies;

    public SellItem(
            EconomyProvider economy,
            WorthSource worth,
            EconomyNotifier notifier,
            Currency defaultCurrency,
            java.util.Collection<Currency> currencies) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.worth = Objects.requireNonNull(worth, "worth");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.currencies = java.util.List.copyOf(currencies);
    }

    /** Sell {@code amount} of {@code material} for {@code seller}, crediting the worth when priced. */
    public SellOutcome sell(com.uxplima.uxmessentials.shared.domain.PlayerRef seller, String material, int amount) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(material, "material");
        if (amount <= 0) {
            notifier.send(seller, EconomyMessageKey.SELL_NOTHING_TO_SELL);
            return SellOutcome.refused();
        }
        Optional<Worth> unit = worth.unitPrice(material);
        if (unit.isEmpty()) {
            notifier.send(seller, EconomyMessageKey.SELL_NOT_SELLABLE, Map.of("item", material));
            return SellOutcome.refused();
        }
        Currency itemCurrency = currencies.stream()
                .filter(c -> c.id().value().equalsIgnoreCase(unit.get().currencyId()))
                .findFirst()
                .orElse(defaultCurrency);
        Money proceeds = Money.of(itemCurrency, unit.get().amount().multiply(BigDecimal.valueOf(amount)));
        Result<Unit, TransferError> credited = economy.credit(seller, proceeds);
        if (credited.isErr()) {
            notifier.send(seller, credited.errorOrThrow().messageKey());
            return SellOutcome.refused();
        }
        notifier.send(
                seller,
                EconomyMessageKey.SELL_SOLD,
                Map.of("count", Integer.toString(amount), "item", material, "amount", notifier.amount(proceeds)));
        return SellOutcome.sold(proceeds);
    }
}
