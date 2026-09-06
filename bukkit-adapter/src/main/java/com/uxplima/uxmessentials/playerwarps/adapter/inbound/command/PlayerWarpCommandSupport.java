package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every player-warps Brigadier command holds: the constructed {@link PlayerWarpServices}
 * and the {@link Messages} catalog (the latter only for the players-only and unknown-player rejections a
 * console or a bad name may see: all player-facing feedback flows through the use cases' {@code MessageSink}).
 * Concrete command classes extend this so each stays focused on building its node and mapping one argument to
 * one use-case call.
 */
@NullMarked
abstract class PlayerWarpCommandSupport {

    /** The catalog key for a console invoking a player-only command. */
    static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;

    /** The catalog key for a named target that could not be resolved to an online player. */
    static final MessageKey UNKNOWN_PLAYER = SharedMessageKey.COMMAND_UNKNOWN_PLAYER;

    final PlayerWarpServices services;
    final CommandFeedback feedback;

    PlayerWarpCommandSupport(PlayerWarpServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, PLAYERS_ONLY, Map.of());
        return null;
    }

    /** Send the command usage format to the sender. */
    final int usage(CommandContext<CommandSourceStack> ctx, String command, String usage, String description) {
        feedback.send(
                ctx.getSource().getSender(),
                com.uxplima.uxmessentials.shared.application.message.SharedMessageKey.COMMAND_USAGE,
                Map.of(
                        "command", command,
                        "usage", usage,
                        "description", description));
        return 0;
    }

    /** The invoking player, or the reserved system identity for console and command-block sources. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        return CommandFeedback.refOf(ctx.getSource().getSender());
    }

    /** Tell {@code sender} the named target was not found, in their locale. */
    final void unknownPlayer(CommandSender sender, String name) {
        feedback.send(sender, UNKNOWN_PLAYER, Map.of("player", name));
    }

    /**
     * Run {@code feedback} on {@code viewer}'s region thread. Inline command feedback calls
     * {@code sender.sendMessage(...)}, a Bukkit interaction, so a continuation reached from an async read must
     * bridge back to the player's entity thread before sending: the homes async-read pattern.
     */
    final void onPlayer(PlayerRef viewer, Runnable feedback) {
        services.scheduler().onEntity(viewer, feedback);
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /**
     * The player's current {@link Position}. Paper marks {@code Player#getLocation()} nullable (null only for
     * an entity with no world, which a connected player never is), so the non-null assertion lives here once
     * rather than at every command.
     */
    static Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }

    /**
     * Resolve the {@code name} argument into a {@link PlayerWarpName} paired with the invoking player and its
     * {@link PlayerRef}, or {@code null}, after the players-only or invalid-name notice, for a console source or
     * a malformed name. This is a cheap tick-thread parse; the caller then hands the resolved target to the
     * scheduler so the repository I/O runs off the command thread.
     */
    final @Nullable Target target(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return null;
        }
        PlayerWarpName name = warpName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return null;
        }
        return new Target(sender, ref(sender), name);
    }

    /**
     * Parse a warp name from raw input, or send the invalid-name notice and return {@code null} when the name is
     * malformed. Keeping the {@link PlayerWarpName#of} guard here means no command handler ever turns a bad name
     * into a stack trace; the rejected text is echoed back so the player sees what failed.
     */
    final @Nullable PlayerWarpName warpName(CommandSender sender, String raw) {
        try {
            return PlayerWarpName.of(raw);
        } catch (IllegalArgumentException invalid) {
            feedback.send(sender, PlayerwarpsMessageKey.PWARP_INVALID_NAME, Map.of("value", raw));
            return null;
        }
    }

    /**
     * Interpret a free-text argument as an optional value: an absent, blank, or {@code "-"} token clears (empty),
     * anything else is the trimmed text. Metadata edits that accept either a value or a clear share this reading.
     */
    static Optional<String> optionalText(String raw) {
        String trimmed = raw.strip();
        return trimmed.isEmpty() || trimmed.equals("-") ? Optional.empty() : Optional.of(trimmed);
    }

    /** The value of a string argument, or {@code ""} when this branch did not supply it (the clear path). */
    static String argOr(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return ctx.getArgument(name, String.class);
        } catch (IllegalArgumentException absent) {
            return "";
        }
    }

    /**
     * The shared shape of a name-only verb: resolve the target then run {@code action} off the tick thread through
     * the scheduler, so the read/write never blocks the command thread and the handler returns immediately.
     */
    final int dispatch(CommandContext<CommandSourceStack> ctx, BiConsumer<PlayerRef, PlayerWarpName> action) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        services.scheduler().async(() -> action.accept(target.who(), target.name()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve {@code playerName} to a (possibly offline) profile, then run {@code action} on it. The shared shape of
     * every verb that takes a player target ({@code transfer}, {@code members}, {@code ban}, {@code whitelist}). Runs
     * inside a scheduler task: an unknown target bridges the not-found notice back to the actor's region thread
     * rather than acting on nobody, so the caller need not repeat the lookup-or-reject dance.
     */
    final void withPlayer(Target target, String playerName, Consumer<PlayerRef> action) {
        Optional<PlayerRef> resolved = services.players().findByName(playerName);
        if (resolved.isEmpty()) {
            onPlayer(target.who(), () -> unknownPlayer(target.sender(), playerName));
            return;
        }
        action.accept(resolved.get());
    }

    /** A {@code /pwarp <literal>} subcommand node gated by {@code permission}. */
    static LiteralArgumentBuilder<CommandSourceStack> verb(String literal, String permission) {
        return Commands.literal(literal).requires(src -> src.getSender().hasPermission(permission));
    }

    /** A {@code name} argument completing (cheaply, no I/O) against the sender's own cached warp names. */
    final RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests(CommandSuggestions.forPlayer(services::ownWarpNames));
    }

    /** A resolved command target: the live sender, its ref, and the parsed warp name. */
    record Target(Player sender, PlayerRef who, PlayerWarpName name) {}
}
