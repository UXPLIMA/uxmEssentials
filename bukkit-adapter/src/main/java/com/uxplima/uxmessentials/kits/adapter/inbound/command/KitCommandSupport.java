package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every kits Brigadier command holds: the constructed {@link KitServices} and the
 * {@link Messages} catalog (the latter only for the players-only and unknown-player rejections a console may
 * see: all player-facing claim feedback flows through the use cases' {@code MessageSink}). Concrete command
 * classes extend this so each stays focused on building its node and mapping its arguments to one use-case
 * call.
 */
@NullMarked
abstract class KitCommandSupport {

    /** The catalog key for a console invoking a player-only command. */
    static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;

    /** The catalog key for a named target that could not be resolved to an online player. */
    static final MessageKey UNKNOWN_PLAYER = SharedMessageKey.COMMAND_UNKNOWN_PLAYER;

    final KitServices services;
    final CommandFeedback feedback;

    KitCommandSupport(KitServices services, Messages messages) {
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
                SharedMessageKey.COMMAND_USAGE,
                Map.of(
                        "command", command,
                        "usage", usage,
                        "description", description));
        return 0;
    }

    /** Tell {@code sender} the named target was not found, in their locale. */
    final void unknownPlayer(CommandSender sender, String name) {
        feedback.send(sender, UNKNOWN_PLAYER, Map.of("player", name));
    }

    /**
     * Resolve the {@code player} selector argument to the first matched online player, reporting unknown-player
     * to {@code sender} when nothing matches. The argument is an
     * {@link io.papermc.paper.command.brigadier.argument.ArgumentTypes#player()} selector, so a name,
     * {@code @p}, {@code @s}, or {@code @r} all resolve here, and a match is always an online player (the PDC
     * a kit stamp or give touches must be loaded).
     */
    final Optional<PlayerRef> resolveSelectorTarget(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> resolved = resolver.resolve(ctx.getSource());
            if (resolved.isEmpty()) {
                unknownPlayer(sender, typedTarget(ctx));
                return Optional.empty();
            }
            return Optional.of(ref(resolved.get(0)));
        } catch (CommandSyntaxException unmatched) {
            unknownPlayer(sender, typedTarget(ctx));
            return Optional.empty();
        }
    }

    /** A {@code player} selector argument, accepting a name or {@code @p}/{@code @s}/{@code @r}. */
    static RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> playerSelectorArgument(
            String argName) {
        return Commands.argument(argName, ArgumentTypes.player()).suggests(CommandSuggestions.singlePlayerTarget());
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /** The non-empty stacks in {@code player}'s main inventory, serialized into the kit's item list. */
    static List<KitItem> inventoryItems(Player player) {
        return KitItemCodec.encodeAll(player.getInventory().getContents());
    }

    /**
     * A kit-id string argument (named {@code argName}) that completes against the kits the invoking player may
     * claim. The kit catalog is held in memory, so the per-keystroke lookup is non-blocking; it is the same
     * permission/consumed filter {@code /kit list} renders, so a player never sees a kit they cannot take.
     */
    final RequiredArgumentBuilder<CommandSourceStack, String> kitNameArgument(String argName) {
        return Commands.argument(argName, StringArgumentType.word())
                .suggests(CommandSuggestions.forPlayer(this::claimableKitIds));
    }

    private List<String> claimableKitIds(PlayerRef viewer) {
        return services.listKits().available(viewer).stream()
                .map(kit -> kit.id().value())
                .toList();
    }

    /** The raw text the sender typed for the {@code player} argument, for the unknown-player rejection. */
    private static String typedTarget(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> "player".equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse("");
    }
}
