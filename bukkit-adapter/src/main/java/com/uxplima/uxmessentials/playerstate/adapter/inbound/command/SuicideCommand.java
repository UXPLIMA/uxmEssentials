package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /suicide} ({@code uxmessentials.suicide.use}): kill yourself. Self-only. There is no target form
 * (§15.6), so the command takes no player argument and a console source is rejected. The {@code Suicide} use
 * case owns the live kill and the confirmation.
 */
@NullMarked
public final class SuicideCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.suicide.use";

    public SuicideCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("suicide")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::suicide)
                .build();
    }

    @Override
    public String description() {
        return "Kill yourself.";
    }

    private int suicide(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.suicide().suicide(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
