package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.WorthOverrideStore;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /setworth <item> <price>|clear}: write a per-item sell-value override into the DB-backed
 * {@link WorthOverrideStore}, the writable counterpart to the config {@code WorthTable}. A positive price
 * upserts the override (overwriting any earlier one for the same material) so {@code /worth} and {@code /sell}
 * price the item at it immediately; a non-positive price or an explicit clear removes the override so the item
 * falls back to its configured worth — or becomes not-sellable when config has none. The override is
 * relational, never PDC, so a set price survives a world rollback like a balance does.
 *
 * <p>Unlike a balance mutation this is a pricing-policy change, so it publishes no economy domain event (the
 * sealed {@code EconomyEvent} family is for aggregate state) and records only through the {@link EconomyAudit}.
 * The operator-only permission is enforced at the command gate. A price may be set in any configured currency;
 * an omitted or unknown currency falls back to the default, matching how the config {@code worth.items} list
 * resolves an item's pay-out currency.
 */
public final class SetWorth {

    private final WorthOverrideStore store;
    private final EconomyNotifier notifier;
    private final EconomyAudit audit;
    private final Currency defaultCurrency;
    private final Collection<Currency> currencies;

    public SetWorth(
            WorthOverrideStore store,
            EconomyNotifier notifier,
            EconomyAudit audit,
            Currency defaultCurrency,
            Collection<Currency> currencies) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.currencies = java.util.List.copyOf(currencies);
    }

    /**
     * Set {@code material}'s worth to {@code price} in the default currency for {@code actor}; a non-positive
     * price clears it.
     */
    public void set(PlayerRef actor, String material, BigDecimal price) {
        set(actor, material, price, defaultCurrency.id().value());
    }

    /**
     * Set {@code material}'s worth to {@code price} in the currency {@code currencyId} for {@code actor}. An
     * unknown currency id falls back to the default, so a typo never persists an unsellable override. A
     * non-positive price clears the override.
     */
    public void set(PlayerRef actor, String material, BigDecimal price, String currencyId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currencyId, "currencyId");
        if (price.signum() <= 0) {
            clear(actor, material);
            return;
        }
        Currency currency = resolve(currencyId);
        store.set(material, price, currency.id().value());
        Money worth = Money.of(currency, price);
        audit.worthSet(actor, material, worth);
        notifier.send(
                actor, EconomyMessageKey.SETWORTH_SET, Map.of("item", material, "amount", notifier.amount(worth)));
    }

    private Currency resolve(String currencyId) {
        return currencies.stream()
                .filter(c -> c.id().value().equalsIgnoreCase(currencyId))
                .findFirst()
                .orElse(defaultCurrency);
    }

    /** Remove {@code material}'s worth override for {@code actor}, noting when none was set. */
    public void clear(PlayerRef actor, String material) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(material, "material");
        if (!store.remove(material)) {
            notifier.send(actor, EconomyMessageKey.SETWORTH_NOT_SET, Map.of("item", material));
            return;
        }
        audit.worthCleared(actor, material);
        notifier.send(actor, EconomyMessageKey.SETWORTH_CLEARED, Map.of("item", material));
    }
}
