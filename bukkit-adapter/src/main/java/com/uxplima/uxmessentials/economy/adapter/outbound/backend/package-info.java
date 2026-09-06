/**
 * The economy's currency backends: each adapts one foreign (or native) economy to the
 * {@link com.uxplima.uxmessentials.economy.application.port.CurrencyBackend} port the routing provider delegates
 * to, so a currency configured to live on Vault, Paper experience, PlayerPoints, CoinsEngine or zEssentials is
 * reached the same way as one on the native ledger.
 *
 * <p>Vault rides the already-resolved {@code EconomyQuery} hook, so no {@code net.milkbowl.vault} type appears
 * here. Paper experience is native: no plugin behind it, online players only. PlayerPoints, CoinsEngine and
 * zEssentials are reached purely by reflection behind a plugin-present guard, so their SDK classes are never
 * named as a field or method signature and a server without the plugin loads none of them.
 * {@link com.uxplima.uxmessentials.economy.adapter.outbound.backend.CurrencyBackends} builds the closed set the
 * server actually has, wrapping every non-atomic backend in {@code SerialisingCurrencyBackend}.
 */
@NullMarked
package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import org.jspecify.annotations.NullMarked;
