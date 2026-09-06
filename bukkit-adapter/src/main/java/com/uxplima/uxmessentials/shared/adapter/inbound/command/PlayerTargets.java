package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jspecify.annotations.NullMarked;

/**
 * The multi-target counterpart to {@link CommandSuggestions#playerArgument}: a player argument that accepts a
 * player selector ({@code @a}, {@code @p}, {@code @s}, {@code @r}) or a single name and resolves to
 * <em>every</em> matched online player. The verbs that apply a per-player effect ({@code /air}, {@code /burn},
 * {@code /feed}, {@code /ice}, {@code /kill}, {@code /nuke}, {@code /lightning}) use this so {@code /burn @a}
 * fans out to all online players instead of acting on one arbitrary match.
 *
 * <p>The argument only <em>suggests</em> the selectors it can resolve to players ({@code @a @p @r @s}), never an
 * entity selector. An entity selector such as {@code @e} is still rejected at parse time if typed by hand: it
 * is simply no longer offered, so the completion list no longer advertises a token the command cannot use.
 *
 * <p>This deliberately wraps {@link ArgumentTypes#players()} rather than {@link ArgumentTypes#player()}: the
 * single-target form rejects a selector that matches more than one entity at parse time, which is exactly the
 * behaviour we are fixing. The plural form parses {@code @a} and hands back the whole list, and a single name
 * still resolves to a one-element list, so the single-name path is unchanged. The resolver runs synchronously
 * on the command (tick) thread; callers iterate the returned list and schedule each player's effect onto that
 * player's own region/entity thread.
 */
@NullMarked
public final class PlayerTargets {

    private PlayerTargets() {}

    /**
     * A {@code name} player argument that accepts a multi-match selector and suggests online players. Mirrors
     * {@link CommandSuggestions#playerArgument} but parses to a {@link PlayerSelectorArgumentResolver} whose
     * {@code resolve} yields the full match list, so a downstream verb can fan out over {@code @a}.
     */
    public static RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> players(String name) {
        Objects.requireNonNull(name, "name");
        return Commands.argument(name, ArgumentTypes.players()).suggests(CommandSuggestions.playerTargets());
    }

    /**
     * Resolve the {@code argName} selector to every matched online player. A single name yields one player; a
     * selector such as {@code @a} yields all matches. The list is empty when the selector matched no online
     * player (or the name is offline). The caller answers that with its own unknown-target message rather than
     * surfacing a raw Brigadier parse error.
     */
    public static List<Player> resolveAll(CommandContext<CommandSourceStack> ctx, String argName) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(argName, "argName");
        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument(argName, PlayerSelectorArgumentResolver.class);
            return resolver.resolve(ctx.getSource());
        } catch (CommandSyntaxException unmatched) {
            // A name with no online player, or a selector that matched nothing. Treated as "no targets" so the
            // caller can answer with the same unknown-target reply the name path uses, never a raw parse error.
            return List.of();
        }
    }
}
