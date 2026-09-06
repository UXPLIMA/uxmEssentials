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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpaall}: open a {@code /tpahere}-direction request to every other online player at once. Each
 * request runs through the same {@link com.uxplima.uxmessentials.teleport.application.RequestTeleport}
 * gates (toggle, block, self): a toggled-off or blocking target is skipped, and, as with a single
 * {@code /tpa}: the requester is told why for each one. The command is a fan-out over the single-target
 * use case, never a privileged bypass.
 */
@NullMarked
public final class TpaAllCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tpa.all";

    public TpaAllCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tpaall")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Request every online player to teleport to you.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef requester = ref(sender);
        for (Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!online.getUniqueId().equals(sender.getUniqueId())) {
                services.requestTeleport().request(requester, ref(online), RequestDirection.TO_REQUESTER);
            }
        }
        return Command.SINGLE_SUCCESS;
    }
}
