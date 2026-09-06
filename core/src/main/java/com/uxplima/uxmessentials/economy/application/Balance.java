package com.uxplima.uxmessentials.economy.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /balance [player] [currency]}: report a wallet's balance in one currency. Reads through the
 * {@link EconomyProvider} (never {@code economy.domain.*}), so the same code serves the native ledger, a
 * Treasury economy, or a legacy Vault economy without knowing which. The currency is resolved at the command
 * boundary before this use case runs ({@link Currency} in hand), defaulting to the configured default when
 * the optional argument is omitted.
 *
 * <p>An offline target is legitimate. {@code /balance <player>} works for a player who is not online,
 * served from the offline read-cache the provider sits in front of, so this use case asks for the balance
 * and renders it without caring about presence.
 */
public final class Balance {

    private final EconomyProvider economy;
    private final EconomyNotifier notifier;

    public Balance(EconomyProvider economy, EconomyNotifier notifier) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Show {@code viewer} their own balance in {@code currency}. */
    public Money own(PlayerRef viewer, Currency currency) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(currency, "currency");
        Money balance = economy.balance(viewer, currency);
        notifier.send(viewer, EconomyMessageKey.WALLET_BALANCE, Map.of("amount", notifier.amount(balance)));
        return balance;
    }

    /** Show {@code viewer} the balance of {@code target} in {@code currency}. */
    public Money other(PlayerRef viewer, PlayerRef target, Currency currency) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(currency, "currency");
        Money balance = economy.balance(target, currency);
        notifier.send(
                viewer,
                EconomyMessageKey.WALLET_BALANCE_OTHER,
                Map.of("player", target.name(), "amount", notifier.amount(balance)));
        return balance;
    }
}
