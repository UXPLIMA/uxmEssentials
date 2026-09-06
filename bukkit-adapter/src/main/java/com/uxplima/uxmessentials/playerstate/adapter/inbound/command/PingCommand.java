package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ping [player]} ({@code uxmessentials.ping.use}): show a player's round-trip latency in milliseconds.
 * Read-only. The {@code ShowPing} use case reads through the
 * {@link com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo} port. The target is a plain
 * online-player name (never an {@code @a}/{@code @p}/{@code @s} selector. One player's ping is a single-target
 * read where a fan-out is nonsensical); the {@code .others} target is gated by the shared
 * {@code uxmessentials.ping.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node.
 */
@NullMarked
public final class PingCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.ping.use";

    public PingCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.ping.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ping")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::show)
                .then(CommandSuggestions.playerArgument("player").executes(this::show))
                .build();
    }

    @Override
    public String description() {
        return "Show a player's ping.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<PlayerRef> target = resolveNamedTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.showPing().showFor(actor(ctx), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
