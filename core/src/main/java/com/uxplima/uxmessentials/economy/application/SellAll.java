package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /sellall}: sell every sellable item in the seller's inventory at its configured {@link WorthSource}
 * worth, the bulk counterpart to {@code /sell}. It reuses the same pricing table and the same DB-backed
 * {@link EconomyProvider} credit (never a PDC stamp, the economy hard invariant). Priced stacks are grouped by
 * the currency they price in, each currency is credited in one move, and a material is reported as sold only
 * after the credit for its currency actually applied, so the inventory the adapter removes and the balance the
 * seller received never diverge. Unpriced materials are silently left in place: no "cannot be sold" spam.
 *
 * <p>An inventory with nothing priced (or empty) is refused with {@link EconomyMessageKey#SELL_NOTHING_TO_SELL}
 * and leaves the inventory untouched; a currency whose credit the clamp rejects leaves that currency's stacks in
 * place and notifies the clamp once, while any other currency still settles. The seller is sent exactly one
 * summary covering every currency that paid out.
 */
public final class SellAll {

    private final EconomyProvider economy;
    private final WorthSource worth;
    private final EconomyNotifier notifier;
    private final Currency defaultCurrency;
    private final java.util.Collection<Currency> currencies;

    public SellAll(
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

    /** Sell every priced material in {@code materials} (id → count) for {@code seller}, crediting the total per currency. */
    public SellAllOutcome sellAll(PlayerRef seller, Map<String, Integer> materials) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(materials, "materials");

        Map<Currency, Map<String, Integer>> pricedByCurrency = groupByCurrency(materials);
        if (pricedByCurrency.isEmpty()) {
            notifier.send(seller, EconomyMessageKey.SELL_NOTHING_TO_SELL);
            return SellAllOutcome.refused();
        }

        Map<String, Integer> sold = new LinkedHashMap<>();
        Map<Currency, Money> proceeds = new LinkedHashMap<>();
        for (Map.Entry<Currency, Map<String, Integer>> bucket : pricedByCurrency.entrySet()) {
            creditCurrency(seller, bucket.getKey(), bucket.getValue(), sold, proceeds);
        }

        if (proceeds.isEmpty()) {
            return SellAllOutcome.refused();
        }
        notifier.send(
                seller,
                EconomyMessageKey.SELLALL_SUMMARY,
                Map.of("count", Integer.toString(sold.size()), "amount", notifier.amounts(proceeds.values())));
        return SellAllOutcome.sold(sold, proceeds);
    }

    /** Group every priced stack under the currency it prices in, with the per-material total worth carried along. */
    private Map<Currency, Map<String, Integer>> groupByCurrency(Map<String, Integer> materials) {
        Map<Currency, Map<String, Integer>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> stack : materials.entrySet()) {
            int count = stack.getValue();
            if (count <= 0) {
                continue;
            }
            Optional<Worth> unit = worth.unitPrice(stack.getKey());
            if (unit.isEmpty()) {
                continue;
            }
            Currency currency = currencyFor(unit.get());
            grouped.computeIfAbsent(currency, c -> new LinkedHashMap<>()).put(stack.getKey(), count);
        }
        return grouped;
    }

    /** Credit one currency's total in a single move; only on success are its stacks recorded as sold. */
    private void creditCurrency(
            PlayerRef seller,
            Currency currency,
            Map<String, Integer> stacks,
            Map<String, Integer> sold,
            Map<Currency, Money> proceeds) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> stack : stacks.entrySet()) {
            Worth unit = worth.unitPrice(stack.getKey()).orElseThrow();
            total = total.add(unit.amount().multiply(BigDecimal.valueOf(stack.getValue())));
        }
        Money credit = Money.of(currency, total);
        Result<Unit, TransferError> credited = economy.credit(seller, credit);
        if (credited.isErr()) {
            notifier.send(seller, credited.errorOrThrow().messageKey());
            return;
        }
        sold.putAll(stacks);
        proceeds.put(currency, credit);
    }

    private Currency currencyFor(Worth unit) {
        return currencies.stream()
                .filter(c -> c.id().value().equalsIgnoreCase(unit.currencyId()))
                .findFirst()
                .orElse(defaultCurrency);
    }
}
