package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Register-or-defer through Bukkit's {@link ServicesManager} (ADR-0004,
 * {@code docs/11-economy-integration.md} §4.1). The economy module is ON by default and registers the native
 * provider as the {@link EconomyProvider} service. <em>unless</em> a provider is already registered (a
 * foreign economy plugin loaded first), in which case it defers, logs, and returns the incumbent so every
 * consumer in this plugin uses the authoritative economy. The decision is made once, at module start; there
 * is no per-call {@code getRegistration} on the hot path.
 *
 * <p>{@link ServicePriority#Normal} is intentional: register-or-defer <em>yields</em> to an incumbent rather
 * than stomping it. An operator who genuinely wants this plugin to win raises the configured priority with
 * eyes open. Both branches emit an audit line surfaced by {@code /uxmess doctor} so an operator can see which
 * economy is authoritative.
 */
@NullMarked
public final class EconomyProviderRegistrar {

    private final ServicesManager services;
    private final Plugin plugin;
    private final Logger log;
    private final ServicePriority priority;

    public EconomyProviderRegistrar(ServicesManager services, Plugin plugin, Logger log, ServicePriority priority) {
        this.services = Objects.requireNonNull(services, "services");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.log = Objects.requireNonNull(log, "log");
        this.priority = Objects.requireNonNull(priority, "priority");
    }

    /**
     * Resolve the provider every consumer in this plugin will use: the incumbent when one is already
     * registered (deferred and logged), otherwise the native provider registered at the configured priority.
     */
    public EconomyProvider registerOrDefer(EconomyProvider nativeProvider) {
        Objects.requireNonNull(nativeProvider, "nativeProvider");
        RegisteredServiceProvider<EconomyProvider> existing = services.getRegistration(EconomyProvider.class);
        if (existing != null) {
            String owner = existing.getPlugin().getName();
            log.info("event=economy_provider_deferred to={}", owner);
            return existing.getProvider();
        }
        services.register(EconomyProvider.class, nativeProvider, plugin, priority);
        log.info("event=economy_provider_registered priority={}", priority);
        return nativeProvider;
    }

    /** Drop this plugin's registration on disable so a reload re-runs register-or-defer cleanly. */
    public void unregister(EconomyProvider provider) {
        services.unregister(EconomyProvider.class, Objects.requireNonNull(provider, "provider"));
    }
}
