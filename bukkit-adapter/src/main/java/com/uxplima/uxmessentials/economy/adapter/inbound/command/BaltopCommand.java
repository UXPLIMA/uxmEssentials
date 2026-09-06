package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /baltop [currency] [page]} (alias {@code /balancetop}): render the ranked leaderboard for one
 * currency. The rows are served from the per-currency snapshot (cached, exempt-filtered, refreshed off-tick
 * {@code docs/11-economy-integration.md} §11), so the command never touches the provider on the tick thread
 * and never re-sorts; the {@link com.uxplima.uxmessentials.economy.application.BalTop} use case pages and
 * renders. A bare leading number is the page in the default currency; a leading word is the currency, with an
 * optional trailing page.
 */
@NullMarked
public final class BaltopCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.baltop";

    public BaltopCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("baltop")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runDefault)
                .then(Commands.literal("refresh")
                        .requires(src -> src.getSender().hasPermission("uxmessentials.economy.admin"))
                        .executes(this::runRefresh))
                .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(this::runDefaultPage))
                .then(currencyArgument()
                        .executes(this::runCurrency)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(this::runCurrencyPage)))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("balancetop");
    }

    @Override
    public String description() {
        return "View the top balances.";
    }

    private int runRefresh(CommandContext<CommandSourceStack> ctx) {
        offTick(() -> {
            services.baltopSnapshots().refreshAll();
            feedback.send(ctx.getSource().getSender(), EconomyMessageKey.BALTOP_REFRESHED, Map.of());
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runDefault(CommandContext<CommandSourceStack> ctx) {
        return show(ctx, defaultCurrency(), 1);
    }

    private int runDefaultPage(CommandContext<CommandSourceStack> ctx) {
        return show(ctx, defaultCurrency(), ctx.getArgument("page", Integer.class));
    }

    private int runCurrency(CommandContext<CommandSourceStack> ctx) {
        return showResolved(ctx, 1);
    }

    private int runCurrencyPage(CommandContext<CommandSourceStack> ctx) {
        return showResolved(ctx, ctx.getArgument("page", Integer.class));
    }

    // The page argument is accepted for command-shape symmetry; the GUI view paginates itself, so it is unused here.
    @SuppressWarnings("unused")
    private int showResolved(CommandContext<CommandSourceStack> ctx, int page) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency =
                services.currencies().find(CurrencyId.of(ctx.getArgument("currency", String.class)));
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        services.baltopView().open(ref(sender), currency.get());
        return Command.SINGLE_SUCCESS;
    }

    // The page argument is accepted for command-shape symmetry; the GUI view paginates itself, so it is unused here.
    @SuppressWarnings("unused")
    private int show(CommandContext<CommandSourceStack> ctx, Currency currency, int page) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.baltopView().open(ref(sender), currency);
        return Command.SINGLE_SUCCESS;
    }
}
