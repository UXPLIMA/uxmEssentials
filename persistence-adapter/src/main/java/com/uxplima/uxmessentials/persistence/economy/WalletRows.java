package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.WalletBalances.WALLET_BALANCES;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code wallet_balances} row and the domain {@link Wallet}. A wallet
 * is a map from {@link Currency} to {@link Money}, so the table is keyed {@code (owner, currency)} and one
 * owner's balances are several rows; this class rebuilds the aggregate from those rows and projects a single
 * balance back to a row for an upsert. UUIDs are the canonical 36-character text and amounts are the stored
 * {@code DECIMAL} the guarded debit compares, never a float, never an opaque JSON blob.
 *
 * <p>A row carries only the currency id, not the full {@link Currency} value object (symbol, precision, the
 * clamp). The configured {@link CurrencyRegistry} resolves the id back to its policy; a row in a currency the
 * registry no longer knows (an operator removed it from config) is skipped rather than failing the load, so a
 * shrunk currency set degrades gracefully instead of stranding the whole wallet.
 */
final class WalletRows {

    private WalletRows() {}

    /** Rebuild a {@link Wallet} from {@code owner}'s balance rows, resolving each currency id through {@code currencies}. */
    static Wallet toWallet(PlayerRef owner, List<? extends Record> rows, CurrencyRegistry currencies) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(currencies, "currencies");
        Map<Currency, Money> balances = new LinkedHashMap<>();
        for (Record row : rows) {
            currencies
                    .find(CurrencyId.of(row.get(WALLET_BALANCES.CURRENCY)))
                    .ifPresent(currency -> balances.put(currency, Money.of(currency, row.get(WALLET_BALANCES.AMOUNT))));
        }
        return Wallet.of(owner, balances);
    }

    /** The stored owner-uuid text for {@code owner}. */
    static String ownerKey(PlayerRef owner) {
        return owner.uuid().toString();
    }

    /** The stored currency-id text for {@code money}'s currency. */
    static String currencyKey(Money money) {
        return money.currency().id().value();
    }

    /** The raw {@link BigDecimal} amount the guarded {@code UPDATE} compares and the balance column stores. */
    static BigDecimal amount(Money money) {
        return money.amount();
    }
}
