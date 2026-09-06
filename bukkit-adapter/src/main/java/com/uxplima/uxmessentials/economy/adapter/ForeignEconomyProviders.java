package com.uxplima.uxmessentials.economy.adapter;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import com.uxplima.uxmessentials.economy.adapter.treasury.TreasuryEconomyAdapter;
import com.uxplima.uxmessentials.economy.adapter.vault.VaultEconomyAdapter;
import com.uxplima.uxmessentials.economy.adapter.vault.VaultEconomyPublisher;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Discovers a foreign economy already on the server and wraps it in this plugin's {@code EconomyProvider}
 * port, Treasury first (modern, multi-currency), then Vault (legacy, single-currency)
 * ({@code docs/11-economy-integration.md} §2). This is the only place that reaches the foreign SDK
 * registrations through the {@code ServicesManager}; the actual SDK imports are confined to the adapter
 * classes in the {@code treasury}/{@code vault} packages, fenced by {@code economyDomainHasNoProviderSdk}.
 *
 * <p>The lookups are gated on the soft-depend plugin being present, so the {@code me.lokka30}/{@code
 * net.milkbowl} symbols are never resolved on a server without the corresponding plugin, which is exactly
 * the register-or-defer condition. When neither is present the result is empty and the native ledger
 * registers as the provider.
 */
@NullMarked
final class ForeignEconomyProviders {

    private ForeignEconomyProviders() {}

    /** The foreign economy to consume, Treasury before Vault, or empty when none is installed. */
    static Optional<EconomyProvider> discover(Plugin plugin, CurrencyRegistry currencies, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(log, "log");
        Optional<EconomyProvider> treasury = treasury(plugin, currencies, log);
        if (treasury.isPresent()) {
            return treasury;
        }
        return vault(plugin, currencies, log);
    }

    /**
     * Publish the native ledger as the server's Vault economy, when Vault is installed.
     *
     * <p>The other direction of the same integration. It lives beside {@link #discover} rather than in the
     * adapter it delegates to, because one optional plugin gets one presence seam: the Vault probe is spelled
     * in this file and nowhere else ({@code SoftDependSeamDriftTest}).
     */
    static void publishNative(
            Plugin plugin,
            EconomyProvider provider,
            CurrencyRegistry currencies,
            ServicePriority priority,
            Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            log.info("event=vault_economy_not_published reason=vault_absent");
            return;
        }
        VaultEconomyPublisher.publish(plugin, provider, currencies.defaultCurrency(), priority, log);
    }

    /** Take that registration back on disable, so a reload publishes once rather than twice. */
    static void withdrawNative(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        VaultEconomyPublisher.withdraw(plugin);
    }

    private static Optional<EconomyProvider> treasury(Plugin plugin, CurrencyRegistry currencies, Logger log) {
        if (plugin.getServer().getPluginManager().getPlugin("Treasury") == null) {
            return Optional.empty();
        }
        // Resolving the Treasury registration (and thus the me.lokka30 symbols) only past the plugin-present
        // guard, so a server without Treasury never loads those classes.
        RegisteredServiceProvider<me.lokka30.treasury.api.economy.EconomyProvider> rsp = plugin.getServer()
                .getServicesManager()
                .getRegistration(me.lokka30.treasury.api.economy.EconomyProvider.class);
        if (rsp == null) {
            return Optional.empty();
        }
        log.info("consuming foreign Treasury economy from {}", rsp.getPlugin().getName());
        return Optional.of(
                new TreasuryEconomyAdapter(rsp.getProvider(), currencies, EconomyMessageKey.CURRENCY_UNSUPPORTED));
    }

    private static Optional<EconomyProvider> vault(Plugin plugin, CurrencyRegistry currencies, Logger log) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return Optional.empty();
        }
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp == null) {
            return Optional.empty();
        }
        log.info("consuming foreign Vault economy from {}", rsp.getPlugin().getName());
        java.util.function.Function<PlayerRef, OfflinePlayer> resolve =
                owner -> plugin.getServer().getOfflinePlayer(owner.uuid());
        return Optional.of(new VaultEconomyAdapter(
                rsp.getProvider(), currencies.defaultCurrency(), resolve, EconomyMessageKey.CURRENCY_UNSUPPORTED));
    }
}
