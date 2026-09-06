package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.NativeCurrencyBackend;
import com.uxplima.uxmessentials.economy.application.SerialisingCurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * Builds the closed set of currency backends this server actually has. The native ledger and Paper experience are
 * unconditional; every other backend is admitted only when its host plugin is present and enabled, checked through
 * the plugin manager before any SDK type is named, so a server without CoinsEngine never classloads a CoinsEngine
 * class. Every non-atomic backend is wrapped by {@code SerialisingCurrencyBackend} on the way in, the native ledger
 * passes straight through, the foreign ones gain a per-owner debit lock.
 *
 * <p>CoinsEngine and zEssentials are multi-currency, so their backends are enumerated from the operator's
 * {@code backends.coinsengine} / {@code backends.zessentials} config maps: one backend per named entry. The
 * placeholder escape hatch is enumerated the same way from {@code backends.placeholder}, but gated on no host plugin
 * it is the backend for an economy nobody wrote a bridge for. An absent map registers none, which is the correct
 * default for a server that runs neither.
 */
public final class CurrencyBackends {

    private CurrencyBackends() {}

    public static CurrencyBackendRegistry discover(
            Server server, Hooks hooks, Logger log, Scheduler scheduler, WalletRepository wallets, ConfigStore config) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(hooks, "hooks");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(wallets, "wallets");
        Objects.requireNonNull(config, "config");

        List<CurrencyBackend> backends = new ArrayList<>();
        backends.add(new NativeCurrencyBackend(wallets));
        backends.add(wrap(new ExpCurrencyBackend(server, scheduler, log)));
        if (present(server, "Vault")) {
            backends.add(wrap(new VaultCurrencyBackend(hooks)));
        }
        if (present(server, "PlayerPoints")) {
            backends.add(wrap(new PlayerPointsCurrencyBackend(server, log)));
        }
        for (String name : config.getKeys("backends.coinsengine")) {
            if (present(server, "CoinsEngine")) {
                backends.add(wrap(new CoinsEngineCurrencyBackend(name, server, log)));
            }
        }
        for (String name : config.getKeys("backends.zessentials")) {
            if (present(server, "zEssentials")) {
                backends.add(wrap(new ZEssentialsCurrencyBackend(name, server, log)));
            }
        }
        for (String name : config.getKeys("backends.placeholder")) {
            backends.add(wrap(PlaceholderCurrencyBackend.fromConfig(name, config, server, log, scheduler)));
        }
        // Available, not in use. Every one of these is a backend a currency may name; native and exp are always
        // here and Vault/PlayerPoints join whenever those plugins are, so a fresh server with no `backends`
        // block still prints three lines. Which of them a currency actually lives on is logged by the wiring.
        backends.forEach(backend -> log.info("event=currency_backend_available id={}", backend.id()));
        return CurrencyBackendRegistry.of(backends);
    }

    private static CurrencyBackend wrap(CurrencyBackend backend) {
        return SerialisingCurrencyBackend.wrapIfNeeded(backend);
    }

    private static boolean present(Server server, String plugin) {
        var found = server.getPluginManager().getPlugin(plugin);
        return found != null && found.isEnabled();
    }
}
