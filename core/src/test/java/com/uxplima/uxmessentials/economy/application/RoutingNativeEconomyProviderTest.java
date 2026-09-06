package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.economy.fakes.RecordingLogger;

/**
 * Runs the {@link EconomyProviderContractTest} against the routing provider serving a native currency. If the
 * routing indirection had cost us the guarded UPDATE, the concurrent double-spend repetition here would be the
 * one to catch it, so this pins that the native path keeps every property the port promises.
 */
final class RoutingNativeEconomyProviderTest extends EconomyProviderContractTest {

    @Override
    protected EconomyProvider newProvider() {
        CurrencyRegistry currencies =
                CurrencyRegistry.of(List.of(Currencies.COINS, Currencies.GEMS), Currencies.COINS.id());
        return new RoutingEconomyProvider(
                CurrencyBackendRegistry.of(List.of(new NativeCurrencyBackend(new InMemoryWalletRepository()))),
                currencies,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new RecordingLogger());
    }
}
