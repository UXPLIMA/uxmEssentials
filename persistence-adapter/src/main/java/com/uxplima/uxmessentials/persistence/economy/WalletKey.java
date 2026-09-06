package com.uxplima.uxmessentials.persistence.economy;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@code (owner, currency)} identity a debounced settle and the offline read-cache are keyed by, the
 * same compound key the {@code wallet_balances} row carries. Equality is by owner uuid and currency id (the
 * owner name is informational on {@link PlayerRef}), so two settles for the same wallet/currency collapse
 * onto one pending entry regardless of which name resolved them.
 *
 * @param owner the wallet owner's uuid
 * @param currency the currency this balance is denominated in
 */
record WalletKey(UUID owner, Currency currency) {

    WalletKey {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
    }

    static WalletKey of(PlayerRef owner, Currency currency) {
        return new WalletKey(Objects.requireNonNull(owner, "owner").uuid(), currency);
    }
}
