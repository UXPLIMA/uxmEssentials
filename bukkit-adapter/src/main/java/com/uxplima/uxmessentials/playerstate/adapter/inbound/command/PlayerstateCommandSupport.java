package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
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
 * Shared collaborators every playerstate Brigadier command holds: the constructed {@link PlayerStateServices}
 * and the {@link Messages} catalog (the latter only for the players-only, unknown-player, and
 * no-permission-for-others rejections: all success feedback flows through the use cases' {@code MessageSink}).
 * Concrete command classes extend this so each stays focused on building its node and mapping its arguments to
 * one use-case call.
 *
 * <p>The {@code .others} target form is gated here by the single shared {@code uxmessentials.playerstate.others}
 * node: {@link #resolveTarget} resolves a named player only when the sender holds it, returning the sender
 * themselves when no name is given. This keeps the per-command nodes to the base verb and the cross-cutting
 * others node, matching docs/permissions.md.
 */
@NullMarked
abstract class PlayerstateCommandSupport {

    /** The cross-cutting node that lets a command target a player other than the sender. */
    static final String OTHERS_PERMISSION = "uxmessentials.playerstate.others";

    private static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;
    private static final MessageKey UNKNOWN_PLAYER = SharedMessageKey.COMMAND_UNKNOWN_PLAYER;
    private static final MessageKey NO_PERMISSION = SharedMessageKey.COMMAND_NO_PERMISSION;

    final PlayerStateServices services;
    final Messages messages;
    final CommandFeedback feedback;

    PlayerstateCommandSupport(PlayerStateServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.feedback = new CommandFeedback(messages);
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        reply(sender, PLAYERS_ONLY, Map.of());
        return null;
    }

    /**
     * Resolve the optional {@code player} argument to one or more targets. When the argument is absent the
     * sender is the sole target; when present (a name or an {@code @a}/{@code @p}/{@code @s}/{@code @r}
     * selector) the matched players are returned only if the sender holds {@link #OTHERS_PERMISSION}
     * otherwise the no-permission rejection is sent and the list is empty. A selector that matches no online
     * player yields the unknown-player rejection and an empty list.
     *
     * <p>The argument is a {@link io.papermc.paper.command.brigadier.argument.ArgumentTypes#players()} selector,
     * so {@code @a} parses and resolves to every online player. The caller applies its per-player effect once
     * per returned target; each target carries its own uuid, so the effect adapter hops to the correct region
     * thread for each. A single name still resolves to a one-element list, leaving the single-target path
     * unchanged.
     */
    final List<PlayerRef> resolveTargets(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        Optional<PlayerSelectorArgumentResolver> resolver = selector(ctx);
        if (resolver.isEmpty()) {
            if (sender instanceof Player player) {
                return List.of(BukkitRefs.toRef(player));
            }
            reply(sender, PLAYERS_ONLY, Map.of());
            return List.of();
        }
        if (!mayTargetOthers(sender)) {
            reply(sender, NO_PERMISSION, Map.of());
            return List.of();
        }
        return resolveSelector(resolver.get(), ctx, sender);
    }

    /**
     * This command's own others node, as {@code uxmessentials.heal.others} for {@code /heal}. Overridden by each
     * command so a target grant can be given one verb at a time; the default is the cross-cutting node, which is
     * what a command with no target form of its own would want.
     */
    String othersNode() {
        return OTHERS_PERMISSION;
    }

    /**
     * Whether the sender may act on somebody else: the cross-cutting {@link #OTHERS_PERMISSION} grants every
     * playerstate command at once, and {@link #othersNode()} grants this one. Either is enough, so an operator
     * chooses the breadth: one node for a full administrator, {@code uxmessentials.heal.others} alone for a medic
     * who must not also empty an inventory.
     */
    final boolean mayTargetOthers(CommandSender sender) {
        return sender.hasPermission(OTHERS_PERMISSION) || sender.hasPermission(othersNode());
    }

    /**
     * Resolve the optional {@code player} selector argument to a single target, taking the first match of an
     * {@link io.papermc.paper.command.brigadier.argument.ArgumentTypes#player()} argument. Used by the
     * single-target write verbs that keep a selector by design ({@code /clearinventory}, {@code /exp}), where
     * naming one player via a selector is reasonable. The single-target <em>read</em> verbs ({@code /playtime},
     * {@code /getpos}, {@code /ping}) deliberately do <em>not</em> use this. They take a plain name through
     * {@link #resolveNamedTarget} so they never surface the {@code @a} selector on a read where a fan-out is
     * nonsensical. Empty means the rejection (no-permission or unknown-player) was already sent.
     */
    final Optional<PlayerRef> resolveTarget(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        return targets.isEmpty() ? Optional.empty() : Optional.of(targets.get(0));
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /**
     * Resolve the optional {@code player} string argument to a single online target, for the read/report verbs
     * that name one player ({@code /playtime}, {@code /getpos}, {@code /ping}). An absent argument is the sender;
     * a present one resolves only when the sender holds {@link #OTHERS_PERMISSION}, matching exactly one online
     * name: anything else is the unknown-player rejection. The argument is a plain {@link CommandSuggestions#playerArgument}
     * word completing against the online roster, so these reads never expose the {@code @a}/{@code @p}/{@code @s}
     * selector syntax: showing one player's stats is a single-target read where fanning out to every player is
     * nonsensical. Empty means the rejection (no-permission or unknown-player) was already sent.
     */
    final Optional<PlayerRef> resolveNamedTarget(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        String typed = typedPlayerArg(ctx);
        if (typed.isEmpty()) {
            if (sender instanceof Player player) {
                return Optional.of(BukkitRefs.toRef(player));
            }
            reply(sender, PLAYERS_ONLY, Map.of());
            return Optional.empty();
        }
        if (!mayTargetOthers(sender)) {
            reply(sender, NO_PERMISSION, Map.of());
            return Optional.empty();
        }
        Player online = Bukkit.getPlayerExact(typed);
        if (online != null) {
            return Optional.of(BukkitRefs.toRef(online));
        }
        reply(sender, UNKNOWN_PLAYER, Map.of("player", typed));
        return Optional.empty();
    }

    /**
     * Resolve the optional {@code player} string argument to a target that may be <em>offline</em>, for the
     * storage-viewing verbs ({@code /invsee}, {@code /endersee}). An absent argument is the sender; a present one
     * resolves only when the sender holds {@link #OTHERS_PERMISSION}. An exact online name first, then a cached
     * offline profile (non-blocking, from the server's user cache); anything else is the unknown-player
     * rejection. Unlike {@link #resolveTarget}, the argument is a plain word so an offline name can be typed (an
     * online-player selector resolves online names only).
     */
    final Optional<PlayerRef> resolveStorageTarget(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        String typed = typedPlayerArg(ctx);
        if (typed.isEmpty()) {
            if (sender instanceof Player player) {
                return Optional.of(BukkitRefs.toRef(player));
            }
            reply(sender, PLAYERS_ONLY, Map.of());
            return Optional.empty();
        }
        if (!mayTargetOthers(sender)) {
            reply(sender, NO_PERMISSION, Map.of());
            return Optional.empty();
        }
        Player online = Bukkit.getPlayerExact(typed);
        if (online != null) {
            return Optional.of(BukkitRefs.toRef(online));
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(typed);
        if (offline != null && offline.getName() != null) {
            return Optional.of(new PlayerRef(offline.getUniqueId(), offline.getName()));
        }
        reply(sender, UNKNOWN_PLAYER, Map.of("player", typed));
        return Optional.empty();
    }

    /** Tab-completion of online player names for the storage verbs' {@code player} argument. */
    static SuggestionProvider<CommandSourceStack> onlinePlayerSuggestions() {
        return (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(online.getName());
                }
            }
            return builder.buildFuture();
        };
    }

    private static String typedPlayerArg(CommandContext<CommandSourceStack> ctx) {
        try {
            return StringArgumentType.getString(ctx, "player");
        } catch (IllegalArgumentException absent) {
            return "";
        }
    }

    private List<PlayerRef> resolveSelector(
            PlayerSelectorArgumentResolver resolver, CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        try {
            List<Player> resolved = resolver.resolve(ctx.getSource());
            if (resolved.isEmpty()) {
                reply(sender, UNKNOWN_PLAYER, Map.of("player", typedTarget(ctx)));
                return List.of();
            }
            return resolved.stream().map(BukkitRefs::toRef).toList();
        } catch (CommandSyntaxException unmatched) {
            // A name with no online player, or a selector that matched nothing, surfaced to the sender as the
            // same unknown-player rejection the name path used, never as a raw Brigadier parse error.
            reply(sender, UNKNOWN_PLAYER, Map.of("player", typedTarget(ctx)));
            return List.of();
        }
    }

    /** The raw text the sender typed for the {@code player} argument, for the unknown-player rejection. */
    private static String typedTarget(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> "player".equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse("");
    }

    private Optional<PlayerSelectorArgumentResolver> selector(CommandContext<CommandSourceStack> ctx) {
        try {
            return Optional.of(ctx.getArgument("player", PlayerSelectorArgumentResolver.class));
        } catch (IllegalArgumentException absent) {
            return Optional.empty();
        }
    }

    private void reply(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        feedback.send(sender, key, placeholders);
    }

    /** The live player actor, or the reserved system actor for console and command-block invocations. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        return CommandFeedback.refOf(ctx.getSource().getSender());
    }
}
