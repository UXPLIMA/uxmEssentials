package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pay <player> <amount> [currency]}: transfer funds to another player. The gates (self-pay,
 * {@code min-pay}, the target's {@code /paytoggle}, the per-currency confirm-threshold) and the atomic move
 * are the {@link com.uxplima.uxmessentials.economy.application.Pay} use case's job; this handler resolves the
 * target name and the currency at the boundary, parses the amount to the currency's precision, and runs the
 * provider call off the tick thread so a foreign provider can never wedge the command (§4.3).
 *
 * <p>The currency is resolved before any provider call (an unknown id is an error listing the valid ids).
 * Above the currency's confirm-threshold the use case stages the pay for {@code /payconfirm}: no money moves
 *, and below it the transfer is immediate.
 */
@NullMarked
public final class PayCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.pay";

    public PayCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pay")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> usage(ctx, "pay", "<player> <amount> [currency]", "Pay money to a player"))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> usage(ctx, "pay", "<player> <amount> [currency]", "Pay money to a player"))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::run)
                                .then(currencyArgument().executes(this::run))))
                .build();
    }

    @Override
    public String description() {
        return "Transfer funds to another player.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        if (!currencyPermitted(sender, currency.get())) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Money> money = amount(ctx.getArgument("amount", String.class), currency.get(), ref(sender));
        if (money.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        offTick(() -> resolveAndPay(sender, ref(sender), targetName, money.get()));
        return Command.SINGLE_SUCCESS;
    }

    private void resolveAndPay(Player sender, PlayerRef from, String targetName, Money amount) {
        Optional<PlayerRef> target = services.players().findOnlineByName(targetName);
        if (target.isEmpty()) {
            rejectUnknownTarget(from, targetName);
            return;
        }
        com.uxplima.uxmessentials.economy.application.PayOutcome outcome =
                services.pay().pay(from, target.get(), amount);
        if (outcome.kind() == com.uxplima.uxmessentials.economy.application.PayOutcome.Kind.STAGED) {
            services.payConfirmationView().open(sender, target.get(), amount);
        }
    }
}
