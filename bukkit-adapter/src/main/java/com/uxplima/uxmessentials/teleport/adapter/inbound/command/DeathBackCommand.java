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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /deathback} (alias {@code /dback}): return the player specifically to their last death point, through
 * the same gated teleport machinery as {@code /back}. It shares {@code /back}'s use node and its death-back
 * gate. The {@code back.on-death} operator setting combined with the {@code uxmessentials.back.ondeath}
 * permission, so a server that disables death-back disables this command too. Unlike {@code /back}, it
 * ignores a pre-teleport capture: if the player's most recent return point is a teleport rather than a death,
 * there is no death location to return to.
 */
@NullMarked
public final class DeathBackCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.back.use";
    private static final String DEATH_BACK_NODE = "uxmessentials.back.ondeath";

    public DeathBackCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("deathback")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("dback");
    }

    @Override
    public String description() {
        return "Return to your last death location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        boolean deathBackAllowed = services.settings().backOnDeathEnabled() && sender.hasPermission(DEATH_BACK_NODE);
        long deathDelaySeconds = services.settings().backDeathDelaySeconds();
        services.captureBack().backToDeath(who, deathBackAllowed, deathDelaySeconds);
        return Command.SINGLE_SUCCESS;
    }
}
