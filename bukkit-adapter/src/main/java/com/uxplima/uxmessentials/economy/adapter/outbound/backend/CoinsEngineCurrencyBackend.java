package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * A named CoinsEngine currency, reached reflectively. CoinsEngine exposes a static facade
 * {@code su.nightexpress.coinsengine.api.CoinsEngineAPI} keyed by a {@code Currency} resolved from its name:
 * {@code getCurrency(name)}, then {@code getBalance(UUID, Currency)} / {@code addBalance(UUID, Currency, double)} /
 * {@code removeBalance(UUID, Currency, double)}. The id is {@code coinsengine:<name>}.
 *
 * <p>The exact API shape can shift between CoinsEngine versions; any mismatch surfaces as a reflective failure the
 * base class logs once and degrades from, so a version bump never throws into a payment. No {@code su.nightexpress}
 * type is named here. The {@code Currency} parameter class is looked up by string name, so the absent path loads
 * nothing.
 */
public final class CoinsEngineCurrencyBackend extends ReflectiveCurrencyBackend {

    private static final String PLUGIN_NAME = "CoinsEngine";
    private static final String API_CLASS = "su.nightexpress.coinsengine.api.CoinsEngineAPI";
    private static final String CURRENCY_CLASS = "su.nightexpress.coinsengine.api.currency.Currency";

    public CoinsEngineCurrencyBackend(String name, Server server, Logger log) {
        super("coinsengine:" + Objects.requireNonNull(name, "name"), PLUGIN_NAME, name, server, log, Precision.DECIMAL);
    }

    @Override
    protected double readBalance(UUID player) throws ReflectiveOperationException {
        Class<?> api = Class.forName(API_CLASS);
        Class<?> currencyType = Class.forName(CURRENCY_CLASS);
        Object money = api.getMethod("getBalance", UUID.class, currencyType).invoke(null, player, currency());
        return ((Number) money).doubleValue();
    }

    @Override
    protected boolean changeBalance(UUID player, BigDecimal amount, boolean deposit)
            throws ReflectiveOperationException {
        Class<?> api = Class.forName(API_CLASS);
        Class<?> currencyType = Class.forName(CURRENCY_CLASS);
        Object currency = currency();
        double value = amount.doubleValue();
        if (!deposit) {
            Object money = api.getMethod("getBalance", UUID.class, currencyType).invoke(null, player, currency);
            if (((Number) money).doubleValue() < value) {
                return false;
            }
        }
        String method = deposit ? "addBalance" : "removeBalance";
        api.getMethod(method, UUID.class, currencyType, double.class).invoke(null, player, currency, value);
        return true;
    }

    /** The CoinsEngine {@code Currency} for this backend's configured name. */
    private Object currency() throws ReflectiveOperationException {
        Object resolved =
                Class.forName(API_CLASS).getMethod("getCurrency", String.class).invoke(null, currency);
        if (resolved == null) {
            throw new ReflectiveOperationException("CoinsEngine has no currency named " + currency);
        }
        return resolved;
    }
}
