package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Map;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /setjail <name>}: save the staff member's current position as a named jail in the DB-backed store, or
 * re-anchor an existing one of the same name. The upsert and the confirmation are the
 * {@link com.uxplima.uxmessentials.moderation.application.SetJail} use case's job; this handler maps the name
 * and the position. The command reuses the shared {@code uxmessentials.moderation.jail} node (matching
 * {@code /jails} and {@code /jailedplayers}), and a console source is rejected: the position is the sender's.
 */
@NullMarked
public final class SetJailCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.jail";

    public SetJailCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setjail")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(this::run)
                        .then(Commands.literal("at").then(positionArguments())))
                .build();
    }

    @Override
    public String description() {
        return "Define a jail at your location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("name", String.class);
        services.setJail().set(actor(ctx), name, position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> positionArguments() {
        return Commands.argument("world", StringArgumentType.word())
                .suggests(CommandSuggestions.loadedWorlds())
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(this::runAt)
                                        .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                                                        .executes(this::runAt))))));
    }

    private int runAt(CommandContext<CommandSourceStack> ctx) {
        String worldName = StringArgumentType.getString(ctx, "world");
        World world = ctx.getSource().getSender().getServer().getWorld(worldName);
        if (world == null) {
            notify(ctx, SharedMessageKey.COMMAND_UNKNOWN_WORLD, Map.of("world", worldName));
            return 0;
        }
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        float yaw = optionalAngle(ctx, "yaw");
        float pitch = optionalAngle(ctx, "pitch");
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            notify(ctx, SharedMessageKey.COMMAND_INVALID_POSITION, Map.of());
            return 0;
        }
        services.setJail()
                .set(
                        actor(ctx),
                        StringArgumentType.getString(ctx, "name"),
                        new Position(BukkitRefs.toRef(world), x, y, z, yaw, pitch));
        return Command.SINGLE_SUCCESS;
    }

    private static float optionalAngle(CommandContext<CommandSourceStack> ctx, String argument) {
        try {
            return (float) DoubleArgumentType.getDouble(ctx, argument);
        } catch (IllegalArgumentException absent) {
            return 0f;
        }
    }
}
