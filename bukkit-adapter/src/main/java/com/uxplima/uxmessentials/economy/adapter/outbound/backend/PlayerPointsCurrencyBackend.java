package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * PlayerPoints as a currency, reached reflectively: {@code org.black_ixx.playerpoints.PlayerPoints.getAPI()} hands
 * back the API, whose {@code look(UUID)}, {@code give(UUID,int)} and {@code take(UUID,int)} read and move integer
 * points. PlayerPoints is single-currency, so there is no sub-currency name and the id is simply {@code playerpoints}.
 *
 * <p>Points are whole numbers, so an amount is rounded to the nearest point at the backend boundary before it
 * crosses into the API. No {@code org.black_ixx} type is named here. Every reference is a string class-name through
 * reflection, so the absent path loads nothing.
 */
public final class PlayerPointsCurrencyBackend extends ReflectiveCurrencyBackend {

    private static final String PLUGIN_NAME = "PlayerPoints";
    private static final String API_CLASS = "org.black_ixx.playerpoints.PlayerPoints";

    public PlayerPointsCurrencyBackend(Server server, Logger log) {
        super("playerpoints", PLUGIN_NAME, null, server, log, Precision.INTEGRAL);
    }

    @Override
    protected double readBalance(UUID player) throws ReflectiveOperationException {
        Object api = api();
        Object points = api.getClass().getMethod("look", UUID.class).invoke(api, player);
        return ((Number) points).doubleValue();
    }

    @Override
    protected boolean changeBalance(UUID player, BigDecimal amount, boolean deposit)
            throws ReflectiveOperationException {
        Object api = api();
        Method method = api.getClass().getMethod(deposit ? "give" : "take", UUID.class, int.class);
        Object ok = method.invoke(api, player, Math.max(0, amount.intValueExact()));
        return Boolean.TRUE.equals(ok);
    }

    private static Object api() throws ReflectiveOperationException {
        Object api = Class.forName(API_CLASS).getMethod("getAPI").invoke(null);
        if (api == null) {
            throw new ReflectiveOperationException("PlayerPoints.getAPI() returned null");
        }
        return api;
    }
}
