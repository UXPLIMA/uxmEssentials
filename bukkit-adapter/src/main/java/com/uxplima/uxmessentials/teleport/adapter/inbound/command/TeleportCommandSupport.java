package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every teleport Brigadier command holds: the constructed {@link TeleportServices}
 * and the {@link Messages} catalog (the latter only for the players-only rejection a console may see
 * all player-facing feedback flows through the use cases' {@code MessageSink}). Concrete command classes
 * extend this so each stays focused on building its node and mapping one argument to one use-case call.
 */
@NullMarked
abstract class TeleportCommandSupport {

    final TeleportServices services;
    final Messages messages;

    TeleportCommandSupport(TeleportServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        return TeleportSenders.requirePlayer(ctx, messages);
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return TeleportSenders.refOf(player);
    }

    /** The command actor: a live player ref, or the stable system ref used by console automation. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        return CommandFeedback.refOf(ctx.getSource().getSender());
    }

    /** A reusable {@code <world> <x> <y> <z> [yaw pitch]} branch for explicit-location commands. */
    static RequiredArgumentBuilder<CommandSourceStack, String> positionArguments(Command<CommandSourceStack> action) {
        return Commands.argument("world", StringArgumentType.word())
                .suggests(CommandSuggestions.loadedWorlds())
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(action)
                                        .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                                                        .executes(action))))));
    }

    /** Parse the shared explicit-location branch and report a precise world/number error. */
    final @Nullable Position explicitPosition(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world", String.class);
        World world = ctx.getSource().getSender().getServer().getWorld(worldName);
        PlayerRef actor = actor(ctx);
        if (world == null) {
            services.notifier()
                    .send(actor, SharedMessageKey.COMMAND_UNKNOWN_WORLD, java.util.Map.of("world", worldName));
            return null;
        }
        double x = ctx.getArgument("x", Double.class);
        double y = ctx.getArgument("y", Double.class);
        double z = ctx.getArgument("z", Double.class);
        float yaw = optionalFloat(ctx, "yaw");
        float pitch = optionalFloat(ctx, "pitch");
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            services.notifier().send(actor, SharedMessageKey.COMMAND_INVALID_POSITION);
            return null;
        }
        return new Position(BukkitRefs.toRef(world), x, y, z, yaw, pitch);
    }

    private static float optionalFloat(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return (float) (double) ctx.getArgument(name, Double.class);
        } catch (IllegalArgumentException absent) {
            return 0f;
        }
    }

    /** Resolve a named, currently-online player to a {@link PlayerRef} and notify the actor on failure. */
    final Optional<PlayerRef> onlineTarget(PlayerRef actor, String name) {
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            services.notifier()
                    .send(actor, TeleportError.TARGET_OFFLINE.messageKey(), java.util.Map.of("player", name));
        }
        return target;
    }
}
