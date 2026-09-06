package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /payconfirm}: confirm and run the payer's staged large transfer. The use case re-validates funds and
 * the threshold from scratch (the balance may have changed since the prompt) and only then runs the guarded
 * transfer. Confirmation re-checks, it never trusts the staged amount blindly
 * ({@code docs/11-economy-integration.md} §9.2). When nothing is staged the use case tells the payer so. The
 * provider call runs off the tick thread.
 */
@NullMarked
public final class PayConfirmCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.pay";

    public PayConfirmCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("payconfirm")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Confirm a large pending pay.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        offTick(() -> services.pay().confirm(ref(sender)));
        return Command.SINGLE_SUCCESS;
    }
}
