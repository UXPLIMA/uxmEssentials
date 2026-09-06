/**
 * The multi-currency provider seam: one façade over the economy's own backend set, so a menu action or
 * condition can target any currency by a short spec string without ever importing a provider SDK. A
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.currency.CurrencyProvider} is a tiny capability over
 * {@code UUID}/{@code double} (balance, has, withdraw, deposit, format), and
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies} maps a spec to the provider that
 * serves it: a configured currency id resolves to that currency over its declared
 * {@link com.uxplima.uxmessentials.economy.application.port.CurrencyBackend}; a bare backend id (e.g.
 * {@code vault}, {@code exp}, {@code coinsengine:gold}) resolves to a synthetic currency over that backend; a
 * blank spec falls back to the configured default; and an unknown spec falls back to a logged no-op.
 *
 * <p>Routing every spec through the {@code CurrencyBackend} registry the economy module owns is the whole point
 * of this seam: a {@code give-money} click and a warp fee now move the same money, so a native currency picks up
 * the guarded compare-and-take debit and the transaction ledger it never had while this façade held a parallel
 * set of providers. The façade depends only on {@code :core}'s backend and currency registries, never on the
 * economy adapter, so the dependency arrow stays {@code shared → core ← economy-adapter}.
 *
 * <p>The capability is entity-thread: callers (Phase-2 economy actions, Phase-3 requirements) run on the viewer's
 * entity thread and invoke a provider there, the same thread the backends require for the players they touch. The
 * providers add no scheduler hop.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import org.jspecify.annotations.NullMarked;
