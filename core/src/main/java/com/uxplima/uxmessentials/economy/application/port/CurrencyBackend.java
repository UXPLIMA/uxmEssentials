package com.uxplima.uxmessentials.economy.application.port;

import java.util.List;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Where the money for one {@link Currency} actually lives. The native ledger is one backend among several;
 * Vault, PlayerPoints, CoinsEngine, zEssentials, Paper experience and a PlaceholderAPI-driven currency are the
 * others. {@code RoutingEconomyProvider} resolves a currency's backend and delegates, so every caller keeps
 * talking to {@link EconomyProvider} and never learns which economy is underneath.
 *
 * <h2>Capabilities, not identities</h2>
 * A caller branches on {@link #worksOffline()} and {@link #atomicDebit()}, never on {@link #id()}. Only the
 * native ledger can promise a guarded compare-and-take; a foreign backend says so and is wrapped by
 * {@code SerialisingCurrencyBackend}, which serialises debits per owner-and-currency inside this JVM. Across a
 * cluster no such promise is available, and config validation refuses to schedule recurring charges against a
 * non-atomic currency unless the operator opts in.
 *
 * <h2>Threading</h2>
 * Every method is I/O and runs off the tick and region threads. Implementations that touch Bukkit do so on a
 * thread the {@code FoliaThreadingDriftTest} allow-list names.
 */
public interface CurrencyBackend {

    /** The stable id a {@code Currency} names in {@code currencies.<id>.backend}, e.g. {@code coinsengine:gold}. */
    String id();

    /** Whether the underlying economy is present and enabled right now. */
    boolean available();

    /** Whether this backend can be written while the owner is offline. False for Paper experience. */
    boolean worksOffline();

    /** True only where check-and-take is one indivisible operation, today, only the native ledger. */
    boolean atomicDebit();

    /** The finest amount this backend can hold. */
    Precision precision();

    /** {@code owner}'s balance in {@code currency}; {@code zero} when they hold none. */
    Money balance(PlayerRef owner, Currency currency);

    /** Single-sided credit, honouring the currency's max-balance clamp by rejecting rather than clamping. */
    Result<Unit, TransferError> credit(PlayerRef owner, Money amount);

    /** Guarded single-sided debit; rejects with {@link TransferError#INSUFFICIENT_FUNDS} below the minimum. */
    Result<Unit, TransferError> debit(PlayerRef owner, Money amount);

    /** Descending ranked balances, bounded by {@code limit}; empty where the backend cannot enumerate accounts. */
    List<BaltopRow> top(Currency currency, int limit);
}
