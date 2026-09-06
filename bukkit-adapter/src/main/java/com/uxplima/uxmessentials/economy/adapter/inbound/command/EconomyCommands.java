package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.EconomyConfig;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyAdminGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds the economy context's Brigadier command surface (docs/10-feature-modules.md §15.4) as
 * {@link CommandRegistration}s over the constructed {@link EconomyServices}. Collected in one greppable table
 * so the literal/permission pairing matches {@code permissions.md} §Economy and the kernel's
 * {@code EconomyCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each. Every
 * command is a thin adapter over the {@code EconomyProvider} port and runs its provider call off the tick
 * thread: none reaches into {@code economy.domain.*}.
 */
@NullMarked
public final class EconomyCommands {

    private EconomyCommands() {}

    /** Every economy command, in surface order. */
    public static List<CommandRegistration> all(
            Plugin plugin, EconomyConfig config, EconomyServices services, Messages messages) {
        return all(plugin, config, services, messages, null);
    }

    /** Every economy command, in surface order, with the bare-{@code /eco} admin GUI when GUIs are enabled. */
    public static List<CommandRegistration> all(
            Plugin plugin,
            EconomyConfig config,
            EconomyServices services,
            Messages messages,
            @Nullable EconomyAdminGuiViews adminGui) {
        List<CommandRegistration> list = new ArrayList<>();

        list.add(new BalanceCommand(services, messages));
        list.add(new PayCommand(services, messages));
        list.add(new PayConfirmCommand(services, messages));
        list.add(new PayAllCommand(services, messages));
        list.add(new PayToggleCommand(services, messages));
        list.add(new BaltopCommand(services, messages));
        list.add(new EcoCommand(plugin, services, messages, adminGui));

        if (config.worthEnabled()) {
            list.add(new WorthCommand(services, messages));
            list.add(new SellCommand(services, messages));
            list.add(new SellAllCommand(services, messages));
            list.add(new SetWorthCommand(services, messages));
        }

        if (config.banknotesEnabled()) {
            list.add(new WithdrawCommand(plugin, services, messages));
            list.add(new DepositCommand(services, messages));
        }

        if (config.bankEnabled()) {
            list.add(new BankCommand(plugin, services, messages));
        }

        if (config.loansEnabled()) {
            list.add(new LoanCommand(plugin, services, messages));
        }

        if (config.walletGuiEnabled()) {
            list.add(new WalletCommand(services, messages));
        }

        if (config.exchangeEnabled()) {
            list.add(new ExchangeCommand(services, messages));
        }

        return List.copyOf(list);
    }
}
