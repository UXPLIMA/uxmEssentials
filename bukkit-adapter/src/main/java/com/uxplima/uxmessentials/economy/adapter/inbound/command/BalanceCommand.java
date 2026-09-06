package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /balance [player] [currency]} (aliases {@code /bal}, {@code /money}): report a wallet's balance in
 * one currency. Reading another player's balance is gated by {@code uxmessentials.economy.balance.others} and
 * works for an offline target: the provider serves it from its offline read-cache. The currency is resolved
 * at this boundary (an unknown id is an error listing the valid ids); the read runs off the tick thread so a
 * foreign provider never blocks the command, and the use case hops the reply back to the requester's region
 * thread.
 */
@NullMarked
public final class BalanceCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.balance";
    private static final String OTHERS_PERMISSION = "uxmessentials.economy.balance.others";

    public BalanceCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("balance")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runOwn)
                .then(CommandSuggestions.playerArgument("player")
                        .executes(this::runOther)
                        .then(currencyArgument().executes(this::runOther)))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("bal", "money");
    }

    @Override
    public String description() {
        return "Show a wallet balance.";
    }

    private int runOwn(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            return unknownCurrency(sender);
        }
        if (!currencyPermitted(sender, currency.get())) {
            return Command.SINGLE_SUCCESS;
        }
        offTick(() -> services.balance().own(ref(sender), currency.get()));
        return Command.SINGLE_SUCCESS;
    }

    private int runOther(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            return runOwn(ctx);
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(actor(ctx));
            return Command.SINGLE_SUCCESS;
        }
        if (sender instanceof Player player && !currencyPermitted(player, currency.get())) {
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        PlayerRef viewer = actor(ctx);
        offTick(() -> resolveAndShow(viewer, targetName, currency.get()));
        return Command.SINGLE_SUCCESS;
    }

    private void resolveAndShow(PlayerRef viewer, String targetName, Currency currency) {
        Optional<PlayerRef> target = services.players().findByName(targetName);
        if (target.isEmpty()) {
            rejectUnknownTarget(viewer, targetName);
            return;
        }
        services.balance().other(viewer, target.get(), currency);
    }

    private int unknownCurrency(Player sender) {
        rejectUnknownCurrency(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
