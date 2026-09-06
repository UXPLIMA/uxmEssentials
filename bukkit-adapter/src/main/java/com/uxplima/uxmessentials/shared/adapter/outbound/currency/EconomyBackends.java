package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;

/**
 * The economy's two closed registries, resolved together once the economy module has wired: the backend set
 * money actually lives on, and the currency set that names which backend holds each currency.
 *
 * <p>The menu-currency façade is constructed while the menu engine wires, well before the economy module runs,
 * so it cannot take these at construction. It holds a supplier of this record instead and reads it on the first
 * click that resolves a currency: by which point {@code EconomyWiring} has filled the reference.
 *
 * @param backends the closed backend set the routing provider resolves each currency against
 * @param currencies the closed currency set, one of which is the configured default
 */
public record EconomyBackends(CurrencyBackendRegistry backends, CurrencyRegistry currencies) {

    public EconomyBackends {
        Objects.requireNonNull(backends, "backends");
        Objects.requireNonNull(currencies, "currencies");
    }
}
