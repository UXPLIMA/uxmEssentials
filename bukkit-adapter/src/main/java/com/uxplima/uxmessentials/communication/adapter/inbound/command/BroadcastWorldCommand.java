package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /broadcastworld <message>} (alias {@code /bcw}, node
 * {@code uxmessentials.communication.broadcastworld}): fire a one-off announcement only to the players in the
 * sender's own world. It mirrors {@code /broadcast} but restricts the fan-out, so it is players-only, the
 * sender's world is the filter, and a console source has none, so it falls through the players-only gate the
 * {@link CommunicationCommandSupport} provides.
 *
 * <p>Like {@code /broadcast} the body is operator-authored MiniMessage content (not a parity-checked
 * {@code MessageKey}) flowing through the {@link BukkitAnnouncerBroadcaster}; the same configured prefix is
 * prepended so it reads like an announcement rather than plain chat, and broadcast opt-outs are still honoured.
 */
@NullMarked
public final class BroadcastWorldCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.communication.broadcastworld";
    private static final String MESSAGE_ARG = "message";

    private final BukkitAnnouncerBroadcaster broadcaster;
    private final String prefix;

    public BroadcastWorldCommand(Messages messages, BukkitAnnouncerBroadcaster broadcaster, String prefix) {
        super(messages);
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("broadcastworld")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument(MESSAGE_ARG, StringArgumentType.greedyString())
                        .executes(this::run))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("bcw");
    }

    @Override
    public String description() {
        return "Send an announcement to players in your world.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String message = StringArgumentType.getString(ctx, MESSAGE_ARG);
        broadcaster.broadcastToWorld(prefix + message, sender.getWorld().getUID());
        return Command.SINGLE_SUCCESS;
    }
}
