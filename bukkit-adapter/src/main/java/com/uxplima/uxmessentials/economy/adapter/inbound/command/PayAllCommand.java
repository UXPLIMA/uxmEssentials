package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
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
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /payall <amount> [currency]}: pay {@code amount} to every online player from the sender's own wallet
 * the player-funded counterpart to the admin {@code /eco giveall}. The fan-out, the per-recipient gates, and
 * the atomic moves are the {@link com.uxplima.uxmessentials.economy.application.PayAll} use case's job (which
 * delegates each leg to {@code Pay}); this handler resolves the currency and parses the amount at the boundary,
 * snapshots the online roster on the tick thread (the Bukkit roster is not safe to read off-tick, the same
 * rule {@link EcoTargets} follows), then runs the transfers off the tick thread so no foreign provider can
 * wedge the command.
 */
@NullMarked
public final class PayAllCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.payall";

    public PayAllCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("payall")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> usage(ctx, "payall", "<amount> [currency]", "Pay all online players"))
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(this::run)
                        .then(currencyArgument().executes(this::run)))
                .build();
    }

    @Override
    public String description() {
        return "Pay every online player at once.";
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
        List<PlayerRef> recipients = EcoTargets.online();
        PlayerRef from = ref(sender);
        offTick(() -> services.payAll().payAll(from, recipients, money.get()));
        return Command.SINGLE_SUCCESS;
    }
}
