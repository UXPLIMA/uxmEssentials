package com.uxplima.uxmessentials.economy.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The economy context's command surface (docs/10-feature-modules.md §15.4) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node (mirroring {@code permissions.md}
 * §Economy) and a factory that produces the kernel-side {@link BrigadierCommand} description. The economy
 * inbound adapter realises the Brigadier node from the started module's context on the next {@code COMMANDS}
 * fire. Collected here so {@code EconomyModule} stays small and the command/permission pairing is one
 * greppable table the permissions guard checks against {@code paper-plugin.yml}.
 *
 * <p>The fine-grained eco-admin nodes ({@code uxmessentials.economy.admin.give|take|set|bulk}) and the
 * data-driven {@code uxmessentials.economy.baltop.exempt} marker are not command base permissions: they are
 * sub-action gates the {@code /eco} handler and the baltop snapshot check, so only the umbrella
 * {@code uxmessentials.economy.admin} node guards the {@code /eco} command itself here.
 */
final class EconomyCommandSurface {

    private EconomyCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec(
                        "balance",
                        "uxmessentials.economy.balance",
                        EconomyCommand.of("balance", List.of("bal", "money"), "Show a wallet balance")),
                spec(
                        "pay",
                        "uxmessentials.economy.pay",
                        EconomyCommand.of("pay", List.of(), "Transfer funds to another player")),
                spec(
                        "payconfirm",
                        "uxmessentials.economy.pay",
                        EconomyCommand.of("payconfirm", List.of(), "Confirm a large pending pay")),
                spec(
                        "paytoggle",
                        "uxmessentials.economy.pay.toggle",
                        EconomyCommand.of("paytoggle", List.of(), "Toggle accepting incoming pay")),
                spec(
                        "payall",
                        "uxmessentials.economy.payall",
                        EconomyCommand.of("payall", List.of(), "Pay every online player")),
                spec(
                        "baltop",
                        "uxmessentials.economy.baltop",
                        EconomyCommand.of("baltop", List.of("balancetop"), "View the top balances")),
                spec(
                        "worth",
                        "uxmessentials.economy.worth",
                        EconomyCommand.of("worth", List.of(), "Report a held item's configured sell value")),
                spec(
                        "sell",
                        "uxmessentials.economy.sell",
                        EconomyCommand.of("sell", List.of(), "Sell held items at their configured worth")),
                spec(
                        "sellall",
                        "uxmessentials.economy.sell",
                        EconomyCommand.of("sellall", List.of(), "Sell every sellable item in your inventory")),
                spec(
                        "withdraw",
                        "uxmessentials.economy.withdraw",
                        EconomyCommand.of("withdraw", List.of(), "Withdraw virtual balance into a physical check")),
                spec(
                        "deposit",
                        "uxmessentials.economy.deposit",
                        EconomyCommand.of("deposit", List.of(), "Deposit physical banknotes into virtual balance")),
                spec(
                        "bank",
                        "uxmessentials.economy.bank",
                        EconomyCommand.of("bank", List.of(), "Shared bank accounts management")),
                spec(
                        "loan",
                        "uxmessentials.economy.loan",
                        EconomyCommand.of("loan", List.of(), "Debtor loans and credit rating")),
                spec(
                        "wallet",
                        "uxmessentials.economy.wallet",
                        EconomyCommand.of("wallet", List.of(), "Show balances across all currencies")),
                spec(
                        "setworth",
                        "uxmessentials.economy.setworth",
                        EconomyCommand.of("setworth", List.of(), "Set an item's sell worth")),
                spec(
                        "eco",
                        "uxmessentials.economy.admin",
                        EconomyCommand.of("eco", List.of("economy"), "Eco-admin balance mutations")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one economy command: literal, aliases, and help text, no Brigadier type. */
    private record EconomyCommand(String literal, List<String> aliases, String description)
            implements BrigadierCommand {

        static EconomyCommand of(String literal, List<String> aliases, String description) {
            return new EconomyCommand(literal, List.copyOf(aliases), description);
        }
    }
}
