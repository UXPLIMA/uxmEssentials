package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

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
import org.jspecify.annotations.NullMarked;

/**
 * {@code /back}: return the player to their captured location through the gated teleport machinery.
 * Death-back is gated separately. The use case is told whether the player may return to a death point by
 * combining the operator's {@code back.on-death} setting with the player's
 * {@code uxmessentials.back.ondeath} permission, so a server can offer {@code /back} after a teleport but
 * not after dying.
 */
@NullMarked
public final class BackCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.back.use";
    private static final String DEATH_BACK_NODE = "uxmessentials.back.ondeath";

    public BackCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("back")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Return to your last location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        boolean deathBackAllowed = services.settings().backOnDeathEnabled() && sender.hasPermission(DEATH_BACK_NODE);
        long deathDelaySeconds = services.settings().backDeathDelaySeconds();
        services.captureBack().back(who, deathBackAllowed, deathDelaySeconds);
        return Command.SINGLE_SUCCESS;
    }
}
