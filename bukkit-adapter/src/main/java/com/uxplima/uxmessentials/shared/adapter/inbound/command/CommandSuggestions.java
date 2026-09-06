package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reusable Brigadier suggestion providers for the inbound command surface. Every provider here runs on the
 * tick thread (Brigadier invokes suggestion callbacks synchronously while the client types), so each one is
 * deliberately allocation-light and never touches I/O. It reads only live in-memory state and filters by the
 * partial token the player has typed so far.
 *
 * <p>The two building blocks cover the two shapes Wave 2 needs: {@link #onlinePlayers()} for a player target
 * that should complete against the online roster, and {@link #fromStrings(Supplier)} for any dynamic name list
 * (currency ids, warp names, kit ids) sourced from an in-memory, side-effect-free supplier. On top of those,
 * {@link #playerTargets()} and {@link #singlePlayerTarget()} complete a Mojang player-selector argument with
 * only the selectors that argument can actually resolve (plus online names), never an entity selector the
 * parser would reject.
 */
@NullMarked
public final class CommandSuggestions {

    private CommandSuggestions() {}

    /**
     * The online players {@code sender} is allowed to see. When the sender is a player, the roster is filtered
     * through that player's {@code canSee} graph (the authoritative vanish-visibility test), so a vanished target
     * below their see level is dropped (and self is always {@code canSee}-true, so a vanished player still finds
     * themselves). A console or no-viewer sender has no visibility graph and sees everyone. This is a cheap
     * in-memory read meant for the tick thread, the single seam every shared player enumeration below routes
     * through, so no completion or picker leaks a hidden player to a viewer who cannot see them.
     */
    public static List<Player> visibleOnlinePlayers(@Nullable CommandSender sender) {
        Player viewer = sender instanceof Player player ? player : null;
        List<Player> visible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (viewer == null || viewer.canSee(online)) {
                visible.add(online);
            }
        }
        return visible;
    }

    /**
     * Suggests the names of every online player the sender may see, prefix-filtered (case-insensitively) by the
     * partial token the sender has typed. Used for player-target arguments that keep a string type because they
     * must also accept offline names: the suggestion only surfaces online matches, but the argument still parses
     * anything typed.
     */
    public static SuggestionProvider<CommandSourceStack> onlinePlayers() {
        // Brigadier resolves suggestions while the player types, off any region tick thread, so an onGlobal hop is
        // wrong here: there is no tick to marshal back to and a synchronous wait would stall the type-ahead. The
        // enumeration reads only player names and the cheap in-memory canSee graph (never mutable entity state); a
        // roster that shifts mid-keystroke at worst offers or omits one stale name for that keystroke, harmless.
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Player online : visibleOnlinePlayers(ctx.getSource().getSender())) {
                String name = online.getName();
                if (matches(name, prefix)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    /** Suggest every currently loaded world name; the list is an in-memory Bukkit registry read. */
    public static SuggestionProvider<CommandSourceStack> loadedWorlds() {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                if (matches(world.getName(), prefix)) {
                    builder.suggest(world.getName());
                }
            }
            return builder.buildFuture();
        };
    }

    /** The selectors that resolve to one or more PLAYERS for a multi-target argument. Never @e/@n (entities). */
    private static final List<String> MULTI_PLAYER_SELECTORS = List.of("@a", "@p", "@r", "@s");

    /** The selectors valid for a SINGLE-target argument: @a is excluded (it can match more than one player). */
    private static final List<String> SINGLE_PLAYER_SELECTORS = List.of("@p", "@r", "@s");

    /** The player-valid selector tokens that match {@code typed} (case-insensitive prefix). Pure: no server access. */
    static List<String> playerSelectorTokens(boolean multi, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> source = multi ? MULTI_PLAYER_SELECTORS : SINGLE_PLAYER_SELECTORS;
        List<String> out = new ArrayList<>();
        for (String selector : source) {
            if (matches(selector, prefix)) {
                out.add(selector);
            }
        }
        return out;
    }

    /** Suggestions for a MULTI-target player argument: @a/@p/@r/@s plus online names. No @e/@n. */
    public static SuggestionProvider<CommandSourceStack> playerTargets() {
        return (ctx, builder) -> selectorSuggestions(ctx, builder, true);
    }

    /** Suggestions for a SINGLE-target player argument: @p/@r/@s plus online names. No @a/@e/@n. */
    public static SuggestionProvider<CommandSourceStack> singlePlayerTarget() {
        return (ctx, builder) -> selectorSuggestions(ctx, builder, false);
    }

    private static CompletableFuture<Suggestions> selectorSuggestions(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, boolean multi) {
        // Offer only the selectors this argument arity actually resolves, then the online roster the sender may see
        // (the default Mojang suggester lists @e/@n too, which a player-target parser rejects), so we override
        // suggestions only, and route the names through the sender's canSee graph so no vanished target leaks.
        for (String token : playerSelectorTokens(multi, builder.getRemaining())) {
            builder.suggest(token);
        }
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player online : visibleOnlinePlayers(ctx.getSource().getSender())) {
            String name = online.getName();
            if (matches(name, prefix)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    /**
     * Suggests every value the supplier returns, prefix-filtered (case-insensitively) by the partial token. The
     * supplier must be side-effect-free and non-blocking. It is invoked on the tick thread per keystroke, so it
     * should read only in-memory state (a registry, an in-memory cache peek). A {@code null} or empty result
     * simply yields no suggestions.
     */
    public static SuggestionProvider<CommandSourceStack> fromStrings(Supplier<? extends Collection<String>> values) {
        Objects.requireNonNull(values, "values");
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            Collection<String> current = values.get();
            if (current != null) {
                for (String value : current) {
                    if (value != null && matches(value, prefix)) {
                        builder.suggest(value);
                    }
                }
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggests a per-viewer list of names, prefix-filtered. The lookup receives the invoking player as a
     * {@link PlayerRef} so a permission-filtered, viewer-scoped source (the warps/kits/pwarps a player may
     * use, the homes they own) can be returned. A non-player source (console) yields no suggestions. The
     * lookup runs on the tick thread per keystroke, so it must read only non-blocking in-memory state.
     */
    public static SuggestionProvider<CommandSourceStack> forPlayer(
            Function<PlayerRef, ? extends Collection<String>> lookup) {
        Objects.requireNonNull(lookup, "lookup");
        return (ctx, builder) -> {
            CommandSender sender = ctx.getSource().getSender();
            if (!(sender instanceof Player player)) {
                return builder.buildFuture();
            }
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            Collection<String> names = lookup.apply(BukkitRefs.toRef(player));
            if (names != null) {
                for (String name : names) {
                    if (name != null && matches(name, prefix)) {
                        builder.suggest(name);
                    }
                }
            }
            return builder.buildFuture();
        };
    }

    private static boolean matches(String candidate, String lowerPrefix) {
        return lowerPrefix.isEmpty() || candidate.toLowerCase(Locale.ROOT).startsWith(lowerPrefix);
    }

    /**
     * A single-word string argument pre-wired to complete against the online roster. This is the drop-in for a
     * player-target argument that must still accept offline names (it stays a string), giving online-name
     * completion without changing how the value is parsed or resolved downstream.
     */
    public static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument(String name) {
        Objects.requireNonNull(name, "name");
        return Commands.argument(name, StringArgumentType.word()).suggests(onlinePlayers());
    }
}
