package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.time.ZoneOffset;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;

/**
 * Runs the {@link EconomyProviderContractTest} against the native ledger provider over an in-memory
 * {@link InMemoryWalletRepository} whose guarded debit/transfer models the database's serialised guard. This
 * is the canonical worked example pinning all four contract properties, including the concurrent
 * double-spend repetition: for the implementation the plugin registers by default.
 */
final class NativeLedgerEconomyProviderTest extends EconomyProviderContractTest {

    @Override
    protected EconomyProvider newProvider() {
        CurrencyRegistry currencies =
                CurrencyRegistry.of(java.util.List.of(Currencies.COINS, Currencies.GEMS), Currencies.COINS.id());
        return new NativeEconomyProvider(
                new InMemoryWalletRepository(), currencies, Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));
    }
}
