package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player visibility subcommands under {@code /hologram}: {@code visibility} (who
 * may see the hologram. {@code ALL} for everyone, {@code PERMISSION} gated by an operator-chosen node, or
 * {@code MANUAL} hidden until shown to a named player), {@code visibilitydistance} (the visibility radius in
 * blocks, 0 = unlimited), and the manual viewer-set commands {@code show <name> <player>} and {@code hide <name>
 * <player>}. Each maps its parsed argument to one {@link Visibility} transition or viewer mutation on the shared
 * {@link HologramServices}; collected here, alongside {@link HologramAppearanceCommand}, so the main {@code
 * /hologram} command class stays focused on its create/edit/move forms while keeping the single literal intact.
 * The mode is validated and the distance clamped here so an invalid value is rejected at the boundary, never
 * inside a use case.
 *
 * <p>The permission node a {@code PERMISSION} hologram requires is the operator's own choice, so it is a dynamic
 * node, not a fixed plugin-declared one. The {@code /hologram} command as a whole stays gated by
 * {@code uxmessentials.hologram.use}; this only sets which node a viewer must hold to see one hologram. {@code
 * show}/{@code hide} resolve their target against the online roster (v1 is online-only; an offline name is
 * rejected with a not-found message) and tab-complete the online players.
 */
@NullMarked
final class HologramVisibilityCommand extends HologramCommandSupport {

    private static final List<String> MODES = List.of("ALL", "PERMISSION", "MANUAL");

    HologramVisibilityCommand(
            HologramServices services, Messages messages, Supplier<? extends Collection<String>> hologramNames) {
        super(services, messages, hologramNames);
    }

    /** The visibility subcommand nodes the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                visibilityNode(), visibilityDistanceNode(), showNode(), hideNode(), blacklistNode(), unblacklistNode());
    }

    private LiteralArgumentBuilder<CommandSourceStack> showNode() {
        return Commands.literal("show")
                .then(nameArgument("name")
                        .then(CommandSuggestions.playerArgument("player").executes(this::show)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> hideNode() {
        return Commands.literal("hide")
                .then(nameArgument("name")
                        .then(CommandSuggestions.playerArgument("player").executes(this::hide)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> blacklistNode() {
        return Commands.literal("blacklist")
                .then(nameArgument("name")
                        .then(CommandSuggestions.playerArgument("player").executes(this::blacklist)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> unblacklistNode() {
        return Commands.literal("unblacklist")
                .then(nameArgument("name")
                        .then(CommandSuggestions.playerArgument("player").executes(this::unblacklist)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> visibilityNode() {
        return Commands.literal("visibility")
                .then(nameArgument("name")
                        .then(choiceArgument("mode", MODES)
                                .executes(this::visibility)
                                .then(Commands.argument("permission", StringArgumentType.word())
                                        .executes(this::visibility))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> visibilityDistanceNode() {
        return Commands.literal("visibilitydistance")
                .then(nameArgument("name")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0))
                                .executes(this::visibilityDistance)));
    }

    private int visibility(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<Visibility.Mode> mode = parseMode(ctx.getArgument("mode", String.class));
        if (mode.isEmpty()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_VISIBILITY_MODE_INVALID, Map.of());
            return 0;
        }
        return switch (mode.orElseThrow()) {
            case ALL -> setMode(ctx, Visibility::toEveryone);
            case MANUAL -> setMode(ctx, Visibility::toManual);
            case PERMISSION -> permissionMode(ctx, sender);
        };
    }

    private int setMode(CommandContext<CommandSourceStack> ctx, java.util.function.UnaryOperator<Visibility> mode) {
        services.visibility().setMode(actor(ctx), nameArg(ctx), mode);
        return Command.SINGLE_SUCCESS;
    }

    private int permissionMode(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        Optional<String> node = optionalArgument(ctx, "permission");
        if (node.isEmpty()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_VISIBILITY_NEEDS_NODE, Map.of());
            return 0;
        }
        services.visibility().setMode(actor(ctx), nameArg(ctx), current -> current.toPermission(node.orElseThrow()));
        return Command.SINGLE_SUCCESS;
    }

    private int visibilityDistance(CommandContext<CommandSourceStack> ctx) {
        int blocks = ctx.getArgument("blocks", Integer.class);
        services.visibility().setDistance(actor(ctx), nameArg(ctx), blocks);
        return Command.SINGLE_SUCCESS;
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player target = onlineTarget(ctx, sender);
        if (target == null) {
            return 0;
        }
        services.viewers().show(actor(ctx), nameArg(ctx), ref(target));
        return Command.SINGLE_SUCCESS;
    }

    private int hide(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player target = onlineTarget(ctx, sender);
        if (target == null) {
            return 0;
        }
        services.viewers().hide(actor(ctx), nameArg(ctx), ref(target));
        return Command.SINGLE_SUCCESS;
    }

    private int blacklist(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player target = onlineTarget(ctx, sender);
        if (target == null) {
            return 0;
        }
        services.blacklist().blacklist(actor(ctx), nameArg(ctx), ref(target));
        return Command.SINGLE_SUCCESS;
    }

    private int unblacklist(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player target = onlineTarget(ctx, sender);
        if (target == null) {
            return 0;
        }
        services.blacklist().unblacklist(actor(ctx), nameArg(ctx), ref(target));
        return Command.SINGLE_SUCCESS;
    }

    /** Resolve the {@code player} argument to an online player, or send the not-found reply and return null. */
    private @org.jspecify.annotations.Nullable Player onlineTarget(
            CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        Player target = Bukkit.getPlayerExact(ctx.getArgument("player", String.class));
        if (target == null) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_PLAYER_NOT_FOUND, Map.of());
        }
        return target;
    }

    private static Optional<Visibility.Mode> parseMode(String raw) {
        for (Visibility.Mode mode : Visibility.Mode.values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> optionalArgument(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return Optional.of(ctx.getArgument(name, String.class));
        } catch (IllegalArgumentException absent) {
            // Brigadier throws when an optional argument node was not supplied on this branch; treat as absent.
            return Optional.empty();
        }
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }
}
