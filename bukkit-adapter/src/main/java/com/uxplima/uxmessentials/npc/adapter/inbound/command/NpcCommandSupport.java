package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators and argument helpers the {@code /npc} command and its sub-handlers hold: the constructed
 * {@link NpcServices} and a {@link CommandFeedback} over the {@link Messages} catalog (the latter for the
 * players-only rejection a console may see, and the per-subcommand validation replies: all other player-facing
 * feedback flows through the use cases' {@code MessageSink}). The root command and each sub-handler extend this
 * so each stays focused on building its node and mapping arguments to use-case calls, while the single
 * {@code /npc} literal stays intact (the sub-handlers contribute argument nodes under it, never a new literal).
 */
@NullMarked
abstract class NpcCommandSupport {

    /** The boolean words suggested for every {@code <true|false>} argument across the {@code /npc} surface. */
    static final List<String> BOOLEAN_WORDS = List.of("true", "false");

    final NpcServices services;
    final CommandFeedback feedback;
    private final Supplier<? extends Collection<String>> npcNames;

    NpcCommandSupport(NpcServices services, Supplier<? extends Collection<String>> npcNames, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.npcNames = Objects.requireNonNull(npcNames, "npcNames");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /**
     * A {@code name} word argument that completes against the current NPC names, the single place every {@code
     * /npc} subcommand sources its name suggestions, so the operator never has to remember a name. The supplier
     * reads the renderer's warm in-memory name set on the tick thread, never the DB.
     */
    final RequiredArgumentBuilder<CommandSourceStack, String> nameArgument() {
        return Commands.argument("name", StringArgumentType.string()).suggests(nameSuggestions());
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

    /** The shared NPC-name suggestion provider, for a {@code name} argument that is not the first under a literal. */
    final SuggestionProvider<CommandSourceStack> nameSuggestions() {
        return CommandSuggestions.fromStrings(npcNames);
    }

    /** A {@code <literal> <name>} subcommand whose name word completes against the current NPC names. */
    final LiteralArgumentBuilder<CommandSourceStack> name(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "npc " + literal, "<name>", "Manage NPC " + literal))
                .then(nameArgument().executes(action));
    }

    /** A {@code <literal> <name> <value…>} subcommand whose value is the greedy rest of the line. */
    final LiteralArgumentBuilder<CommandSourceStack> greedy(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .executes(ctx -> usage(ctx, "npc " + literal, "<name> <text>", "Set NPC " + literal))
                .then(nameArgument()
                        .executes(ctx -> usage(ctx, "npc " + literal, "<name> <text>", "Set NPC " + literal))
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(action)));
    }

    /** A {@code <literal> <name> <true|false>} subcommand whose value word completes against the boolean choices. */
    final LiteralArgumentBuilder<CommandSourceStack> bool(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(nameArgument()
                        .then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .suggests((ctx, builder) -> suggest(builder, BOOLEAN_WORDS))
                                .executes(action)));
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, NpcMessageKey.NPC_PLAYERS_ONLY, Map.of());
        return null;
    }

    static NpcName nameArg(CommandContext<CommandSourceStack> ctx) {
        return NpcName.of(ctx.getArgument("name", String.class));
    }

    static String value(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("value", String.class);
    }

    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    static Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }

    /**
     * Parse {@code word} case-insensitively to a Bukkit {@link EntityType} an NPC can render as, {@code PLAYER}
     * (the fake-player path), any living-entity type, or a supported display/interaction type, or {@code null} for
     * an unknown or unrenderable type, so the caller can report {@code NPC_INVALID_ENTITY_TYPE}. Shared by
     * {@code /npc create [type]} and {@code /npc type}.
     */
    static @Nullable EntityType parseRenderableType(String word) {
        EntityType type;
        try {
            type = EntityType.valueOf(word.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        return isRenderableType(type) ? type : null;
    }

    /** Whether an NPC may render as {@code type}: a fake player, a living entity, or a supported display type. */
    static boolean isRenderableType(EntityType type) {
        return isLiving(type) || isSupportedDisplayType(type);
    }

    /** Whether an NPC may render as {@code type}: a fake player, or any entity whose class is a living entity. */
    static boolean isLiving(EntityType type) {
        if (type == EntityType.PLAYER) {
            return true;
        }
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && LivingEntity.class.isAssignableFrom(entityClass);
    }

    /**
     * Whether {@code type} is a non-living display/interaction entity an NPC can render as. These are packet-spawned
     * like any other type and carry their visual through display-content type-data keys; the set grows as each
     * type's content support lands. Currently: {@link EntityType#INTERACTION} (an invisible, sized, clickable
     * hitbox that still carries the NPC's click command and action chain), {@link EntityType#BLOCK_DISPLAY} and
     * {@link EntityType#ITEM_DISPLAY} (a floating block/item, content set via the {@code block}/{@code item} keys).
     */
    static boolean isSupportedDisplayType(EntityType type) {
        return type == EntityType.INTERACTION
                || type == EntityType.BLOCK_DISPLAY
                || type == EntityType.ITEM_DISPLAY
                || type == EntityType.TEXT_DISPLAY;
    }

    /** Suggest the {@code words} that start with what the operator has typed, case-insensitively. */
    static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, List<String> words) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (word.startsWith(prefix)) {
                builder.suggest(word);
            }
        }
        return builder.buildFuture();
    }
}
