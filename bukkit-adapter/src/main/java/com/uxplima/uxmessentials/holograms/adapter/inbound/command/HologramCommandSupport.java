package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators the holograms Brigadier command holds: the constructed {@link HologramServices} and
 * the {@link Messages} catalog (the latter only for the players-only rejection a console may see, all other
 * player-facing feedback flows through the use cases' {@code MessageSink}). The single {@code /hologram}
 * command class extends this so it stays focused on building its node and mapping arguments to use-case calls.
 */
@NullMarked
abstract class HologramCommandSupport {

    final HologramServices services;
    final Messages messages;
    final CommandFeedback feedback;

    /** A side-effect-free, non-blocking source of the current hologram names for tab-completion. */
    private final Supplier<? extends Collection<String>> hologramNames;

    HologramCommandSupport(
            HologramServices services, Messages messages, Supplier<? extends Collection<String>> hologramNames) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.hologramNames = Objects.requireNonNull(hologramNames, "hologramNames");
        this.feedback = new CommandFeedback(messages);
    }

    /**
     * A single-word {@code name} argument pre-wired to complete against the current hologram names. The names
     * are read from the in-memory hologram set on each keystroke, so the completion is allocation-light and
     * never touches the database (the set is warm after the one load on enable).
     */
    final RequiredArgumentBuilder<CommandSourceStack, String> nameArgument(String literal) {
        return Commands.argument(literal, StringArgumentType.string()).suggests(nameSuggestions());
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

    /** The command actor: a live player ref, or the stable system ref used by console automation. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        return CommandFeedback.refOf(ctx.getSource().getSender());
    }

    /** The suggestion provider over the current hologram names, reusable for any {@code name} argument. */
    final SuggestionProvider<CommandSourceStack> nameSuggestions() {
        return CommandSuggestions.fromStrings(hologramNames);
    }

    /** A literal argument that completes against a fixed set of choices (enum values, booleans). */
    static RequiredArgumentBuilder<CommandSourceStack, String> choiceArgument(String literal, List<String> choices) {
        return Commands.argument(literal, StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(() -> choices));
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, HologramsMessageKey.HOLOGRAM_PLAYERS_ONLY, Map.of());
        return null;
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /** The player's current {@link Position}. */
    static Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }
}
