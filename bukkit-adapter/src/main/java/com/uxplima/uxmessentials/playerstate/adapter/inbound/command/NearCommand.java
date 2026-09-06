package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /near [radius]} ({@code uxmessentials.near.use}): list the players within a radius of you, nearest
 * first. Self-only: it reads your own location. The {@code ListNearby} use case clamps the radius and renders
 * the list; this handler maps the optional radius argument.
 */
@NullMarked
public final class NearCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.near.use";

    public NearCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("near")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::nearDefault)
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(this::nearRadius))
                .build();
    }

    @Override
    public String description() {
        return "List nearby players.";
    }

    private int nearDefault(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listNearby().near(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int nearRadius(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listNearby().near(ref(sender), ctx.getArgument("radius", Integer.class));
        return Command.SINGLE_SUCCESS;
    }
}
