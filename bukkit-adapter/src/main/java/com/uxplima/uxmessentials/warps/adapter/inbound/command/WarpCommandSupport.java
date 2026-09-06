package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every warps Brigadier command holds: the constructed {@link WarpServices} and the
 * {@link Messages} catalog (the latter only for the players-only rejection a console may see, all
 * player-facing feedback flows through the use cases' {@code MessageSink}). Concrete command classes extend
 * this so each stays focused on building its node and mapping one argument to one use-case call.
 */
@NullMarked
abstract class WarpCommandSupport {

    /** The catalog key for a console invoking a player-only command. */
    static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;

    /** The catalog key for a named target that could not be resolved to an online player. */
    static final MessageKey UNKNOWN_PLAYER = SharedMessageKey.COMMAND_UNKNOWN_PLAYER;

    final WarpServices services;
    final CommandFeedback feedback;

    WarpCommandSupport(WarpServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** The command actor: a live player ref, or the stable system ref used by console automation. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        return CommandFeedback.refOf(ctx.getSource().getSender());
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

    /** Tell {@code sender} the named target was not found, in their locale. */
    final void unknownPlayer(CommandSender sender, String name) {
        feedback.send(sender, UNKNOWN_PLAYER, Map.of("player", name));
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /**
     * Run {@code feedback} on {@code viewer}'s region thread. Inline command feedback calls
     * {@code sender.sendMessage(...)}, a Bukkit interaction, so a continuation reached from an async read must
     * bridge back to the player's entity thread before sending, the same bridge {@code /pwarp} uses.
     */
    final void onPlayer(PlayerRef viewer, Runnable feedback) {
        services.scheduler().onEntity(viewer, feedback);
    }

    /**
     * A {@code name} string argument that completes against the warps the invoking player may use. The warp
     * repository is held in memory, so the per-keystroke lookup is non-blocking; the suggestion is the same
     * permission-filtered set {@code /warps} renders, so a player never sees a warp they cannot teleport to.
     */
    final RequiredArgumentBuilder<CommandSourceStack, String> warpNameArgument() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames));
    }

    final List<String> usableWarpNames(PlayerRef viewer) {
        return services.listWarps().available(viewer).stream()
                .map(warp -> warp.name().value())
                .toList();
    }

    /**
     * The player's current {@link Position}. Paper marks {@code Player#getLocation()} nullable (null only
     * for an entity with no world, which a connected player never is), so the non-null assertion lives here
     * once rather than at every command.
     */
    static Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }
}
