package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /burn <seconds> [player]} ({@code uxmessentials.burn.use}): set a player on fire for a number of
 * seconds, the inverse of {@code /ext}. The seconds argument is bounded by Brigadier and clamped to a sane
 * range in the domain ({@link BurnDuration}). The {@code .others} target is gated by the shared
 * {@code uxmessentials.burn.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node; the {@code Burn} use case owns the effect and feedback.
 */
@NullMarked
public final class BurnCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.burn.use";

    public BurnCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.burn.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("burn")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, BurnDuration.MAX_SECONDS))
                        .executes(this::burn)
                        .then(PlayerTargets.players("player").executes(this::burn)))
                .build();
    }

    @Override
    public String description() {
        return "Set a player on fire for some seconds.";
    }

    private int burn(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        BurnDuration duration = BurnDuration.ofSeconds(ctx.getArgument("seconds", Integer.class));
        for (PlayerRef target : targets) {
            services.burn().burnFor(actor(ctx), target, duration);
        }
        return Command.SINGLE_SUCCESS;
    }
}
