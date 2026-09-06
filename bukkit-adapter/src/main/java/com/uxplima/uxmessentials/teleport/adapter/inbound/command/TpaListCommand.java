package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpalist} (alias {@code /tprequests}): shows the invoking player who is waiting on their reply.
 * A no-argument, self-only verb that delegates to
 * {@link com.uxplima.uxmessentials.teleport.application.ListPendingRequests}; it shares {@code tpa.use}
 * with the resolution verbs, since anyone who can {@code /tpaccept} should be able to see the queue.
 */
@NullMarked
public final class TpaListCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String LITERAL = "tpalist";
    private static final String PERMISSION = "uxmessentials.tpa.use";

    public TpaListCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "List players waiting on your teleport reply";
    }

    @Override
    public List<String> aliases() {
        return List.of("tprequests");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listPendingRequests().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
