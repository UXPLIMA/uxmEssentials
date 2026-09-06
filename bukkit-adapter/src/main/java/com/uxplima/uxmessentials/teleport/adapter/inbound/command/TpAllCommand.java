package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpall}: pull every other online player to the invoking staff member at once. Each hop runs
 * through the async {@code TeleportExecutor} with no warmup, a staff verb. The destination is captured
 * once from the actor's location so a hop in progress does not chase a moving target.
 */
@NullMarked
public final class TpAllCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tp.all";

    public TpAllCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tpall")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Pull every online player to you.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Position destination = TeleportRefs.positionOf(sender);
        for (Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!online.getUniqueId().equals(sender.getUniqueId())) {
                services.executor().teleport(ref(online), Destination.at(destination), TeleportKind.ADMIN);
            }
        }
        services.notifier().send(ref(sender), TeleportMessageKey.TP_DONE);
        return Command.SINGLE_SUCCESS;
    }
}
