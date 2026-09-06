package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The real {@link EconomyQuery}, backed by the Vault {@link Economy} registered in the {@code ServicesManager}.
 * This is the only class in the hooks package that imports {@code net.milkbowl.vault.economy}; it is reached
 * solely from {@link VaultEconomyHook#whenPresent}, past {@code Hooks}' present-guard, so it loads only on a
 * server that actually has Vault installed: the no-op {@link EconomyQuery#ABSENT} carries none of these types.
 *
 * <p>The economy can be absent even when Vault is present (Vault installed with no economy plugin behind it):
 * the registration is resolved once in the constructor and may be {@code null}, in which case every operation
 * is a no-op and {@link #available()} reports false. Vault's economy API is entity-thread only, so callers
 * invoke this on the viewer's entity thread (see {@link EconomyQuery}).
 */
@NullMarked
final class VaultEconomyService implements EconomyQuery {

    private final Server server;

    @Nullable private final Economy economy;

    VaultEconomyService(Server server) {
        this.server = Objects.requireNonNull(server, "server");
        RegisteredServiceProvider<Economy> registration =
                server.getServicesManager().getRegistration(Economy.class);
        this.economy = registration == null ? null : registration.getProvider();
    }

    @Override
    public boolean available() {
        return active() != null;
    }

    @Override
    public double balance(UUID player) {
        Economy active = active();
        return active == null ? 0 : active.getBalance(offline(player));
    }

    @Override
    public boolean has(UUID player, double amount) {
        Economy active = active();
        return active != null && active.has(offline(player), amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        Economy active = active();
        return active != null && active.withdrawPlayer(offline(player), amount).transactionSuccess();
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        Economy active = active();
        return active != null && active.depositPlayer(offline(player), amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        Economy active = active();
        return active == null ? Double.toString(amount) : active.format(amount);
    }

    private @Nullable Economy active() {
        Economy resolved = this.economy;
        return resolved != null && resolved.isEnabled() ? resolved : null;
    }

    private OfflinePlayer offline(UUID player) {
        return server.getOfflinePlayer(Objects.requireNonNull(player, "player"));
    }
}
