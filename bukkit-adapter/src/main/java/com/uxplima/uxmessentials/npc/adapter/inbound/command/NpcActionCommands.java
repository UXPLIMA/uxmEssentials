package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.EquipmentPayloads;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ClickActionValueCheck;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /npc action <add|list|remove|clear>} subcommands: edit the ordered click-action chain. {@code add}
 * takes a trigger word (left/right/any), a type word, and the rest of the line as the value. The type word is
 * one of the effects ({@code console}, {@code player}, {@code message}, {@code actionbar}, {@code title},
 * {@code sound}, {@code connect}, {@code give}), the sequencer's {@code delay} or {@code random}, or a gate
 * ({@code chance}, {@code permission}, {@code condition}, {@code cost}); the cheap value shapes are validated at
 * add time, while the gates and text effects accept any value. {@code give hand} is a special value: instead of a
 * material it captures the sender's currently-held item (with its NBT) as a serialized token. Collected here so
 * the root {@code /npc} command stays focused while keeping the single literal intact.
 */
@NullMarked
final class NpcActionCommands extends NpcCommandSupport {

    /** The accepted {@code action add} trigger words, also the tab suggestions. */
    private static final List<String> TRIGGER_WORDS = List.of("left", "right", "any");
    /** The accepted {@code action add} type words, also the tab suggestions. */
    private static final List<String> TYPE_WORDS = List.of(
            "console",
            "player",
            "player_op",
            "message",
            "actionbar",
            "title",
            "sound",
            "connect",
            "delay",
            "random",
            "chance",
            "permission",
            "condition",
            "cost",
            "give");

    /** The {@code give} value word that captures the sender's currently-held item instead of naming a material. */
    private static final String HAND_KEYWORD = "hand";

    NpcActionCommands(
            NpcServices services,
            java.util.function.Supplier<? extends java.util.Collection<String>> npcNames,
            Messages messages) {
        super(services, npcNames, messages);
    }

    /** The {@code action} subcommand node the {@code /npc} literal attaches. */
    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("action")
                .then(actionAddNode())
                .then(actionEditNode("add_before", this::actionAddBefore))
                .then(actionEditNode("add_after", this::actionAddAfter))
                .then(actionEditNode("set", this::actionSet))
                .then(actionMoveNode("move_up", this::actionMoveUp))
                .then(actionMoveNode("move_down", this::actionMoveDown))
                .then(Commands.literal("list").then(nameArgument().executes(this::actionList)))
                .then(Commands.literal("remove")
                        .then(nameArgument()
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(this::actionRemove))))
                .then(Commands.literal("clear").then(nameArgument().executes(this::actionClear)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> actionAddNode() {
        return Commands.literal("add")
                .then(nameArgument()
                        .then(Commands.argument("trigger", StringArgumentType.word())
                                .suggests(this::suggestTriggers)
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(this::suggestTypes)
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(this::actionAdd)))));
    }

    /** A {@code <literal> <name> <index> <trigger> <type> <value…>} node, for the index-relative action edits. */
    private LiteralArgumentBuilder<CommandSourceStack> actionEditNode(
            String literal, Command<CommandSourceStack> exec) {
        return Commands.literal(literal)
                .then(nameArgument()
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .then(Commands.argument("trigger", StringArgumentType.word())
                                        .suggests(this::suggestTriggers)
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(this::suggestTypes)
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(exec))))));
    }

    /** A {@code <literal> <name> <index>} node, for the reorder edits. */
    private LiteralArgumentBuilder<CommandSourceStack> actionMoveNode(
            String literal, Command<CommandSourceStack> exec) {
        return Commands.literal(literal)
                .then(nameArgument()
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(exec)));
    }

    private int actionAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ClickAction action = parseAction(sender, ctx);
        if (action == null) {
            return 0;
        }
        services.addAction().add(actor(ctx), nameArg(ctx), action);
        return Command.SINGLE_SUCCESS;
    }

    private int actionAddBefore(CommandContext<CommandSourceStack> ctx) {
        return insertAt(ctx, false);
    }

    private int actionAddAfter(CommandContext<CommandSourceStack> ctx) {
        return insertAt(ctx, true);
    }

    private int insertAt(CommandContext<CommandSourceStack> ctx, boolean after) {
        CommandSender sender = ctx.getSource().getSender();
        ClickAction action = parseAction(sender, ctx);
        if (action == null) {
            return 0;
        }
        services.insertAction()
                .insert(actor(ctx), nameArg(ctx), ctx.getArgument("index", Integer.class), after, action);
        return Command.SINGLE_SUCCESS;
    }

    private int actionSet(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ClickAction action = parseAction(sender, ctx);
        if (action == null) {
            return 0;
        }
        services.setAction().set(actor(ctx), nameArg(ctx), ctx.getArgument("index", Integer.class), action);
        return Command.SINGLE_SUCCESS;
    }

    private int actionMoveUp(CommandContext<CommandSourceStack> ctx) {
        return moveAction(ctx, true);
    }

    private int actionMoveDown(CommandContext<CommandSourceStack> ctx) {
        return moveAction(ctx, false);
    }

    private int moveAction(CommandContext<CommandSourceStack> ctx, boolean up) {
        services.moveAction().move(actor(ctx), nameArg(ctx), ctx.getArgument("index", Integer.class), up);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Parse the {@code trigger}/{@code type}/{@code value} args into a {@link ClickAction}, sending the matching
     * validation feedback and returning {@code null} when any part is invalid (or a {@code give hand} capture
     * failed). Shared by {@code add}, {@code add_before}/{@code add_after} and {@code set}.
     */
    private @Nullable ClickAction parseAction(CommandSender sender, CommandContext<CommandSourceStack> ctx) {
        ClickTrigger trigger = parseTrigger(ctx.getArgument("trigger", String.class));
        if (trigger == null) {
            feedback.send(
                    sender,
                    NpcMessageKey.NPC_INVALID_TRIGGER,
                    Map.of("trigger", ctx.getArgument("trigger", String.class)));
            return null;
        }
        String typeWord = ctx.getArgument("type", String.class);
        ClickActionType type = ClickActionValueCheck.parseType(typeWord).orElse(null);
        if (type == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_ACTION_TYPE, Map.of("type", typeWord));
            return null;
        }
        String value = resolveValue(sender, type, value(ctx));
        if (value == null) {
            return null; // the capture failed (empty hand) and its feedback was already sent
        }
        ClickActionValueCheck.Result check = ClickActionValueCheck.check(type, value);
        if (check instanceof ClickActionValueCheck.Result.Invalid invalid) {
            feedback.send(
                    sender,
                    NpcMessageKey.NPC_INVALID_ACTION_VALUE,
                    Map.of("value", value, "type", typeWord.toLowerCase(Locale.ROOT), "hint", invalid.hint()));
            return null;
        }
        return new ClickAction(trigger, type, value);
    }

    /**
     * Resolve the value to store: for a {@code give hand} the sender's held item is captured as a serialized
     * token (a single-quantity clone with all its NBT), failing with feedback on an empty hand; every other case
     * stores the raw value as typed. Returns {@code null} when the capture failed, signalling the handler to stop.
     */
    private @Nullable String resolveValue(CommandSender sender, ClickActionType type, String rawValue) {
        if (type != ClickActionType.GIVE || !rawValue.strip().equalsIgnoreCase(HAND_KEYWORD)) {
            return rawValue;
        }
        if (!(sender instanceof Player player)) {
            feedback.send(sender, NpcMessageKey.NPC_PLAYERS_ONLY, Map.of());
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            feedback.send(sender, NpcMessageKey.NPC_GIVE_EMPTY_HAND, Map.of());
            return null;
        }
        return EquipmentPayloads.serialize(hand);
    }

    private int actionList(CommandContext<CommandSourceStack> ctx) {
        services.listActions().list(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int actionRemove(CommandContext<CommandSourceStack> ctx) {
        services.removeAction().remove(actor(ctx), nameArg(ctx), ctx.getArgument("index", Integer.class));
        return Command.SINGLE_SUCCESS;
    }

    private int actionClear(CommandContext<CommandSourceStack> ctx) {
        services.clearActions().clear(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestTriggers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, TRIGGER_WORDS);
    }

    private CompletableFuture<Suggestions> suggestTypes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, TYPE_WORDS);
    }

    /** Map the operator's trigger word (left/right/any) to a {@link ClickTrigger}, or {@code null} when unknown. */
    private static @Nullable ClickTrigger parseTrigger(String word) {
        return switch (word.strip().toLowerCase(Locale.ROOT)) {
            case "left" -> ClickTrigger.LEFT_CLICK;
            case "right" -> ClickTrigger.RIGHT_CLICK;
            case "any" -> ClickTrigger.ANY;
            default -> null;
        };
    }
}
