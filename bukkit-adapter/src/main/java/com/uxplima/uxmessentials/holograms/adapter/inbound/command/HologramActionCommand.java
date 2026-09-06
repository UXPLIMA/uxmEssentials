package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

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
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ClickActionValueCheck;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /hologram action <name> <add|add_before|add_after|set|move_up|move_down|list|remove|clear>}
 * subcommands: edit the ordered click-action chain that fires when a player clicks the hologram. {@code add} takes
 * a trigger word (left/right/any), a type word, and the rest of the line as the value. The type word is one of
 * the effects ({@code console}, {@code player}, {@code player_op}, {@code message}, {@code actionbar},
 * {@code title}, {@code sound}, {@code connect}), the sequencer's {@code delay} or {@code random}, or a gate
 * ({@code chance}, {@code permission}, {@code condition}, {@code cost}, {@code give}); the cheap value shapes are
 * validated at add time through the shared {@link ClickActionValueCheck}, while the gates and text effects accept
 * any value. {@code give hand} is a special value: instead of a material it captures the sender's currently-held
 * item (with its NBT) as a serialized token. Indices are 1-based at the command boundary; the use cases own the
 * conversion to chain positions. Collected here so the root {@code /hologram} command stays focused while keeping
 * the single literal intact.
 */
@NullMarked
final class HologramActionCommand extends HologramCommandSupport {

    /** The accepted trigger words, also the tab suggestions. */
    private static final List<String> TRIGGER_WORDS = List.of("left", "right", "any");
    /** The accepted type words, also the tab suggestions. */
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

    HologramActionCommand(
            HologramServices services, Messages messages, Supplier<? extends Collection<String>> hologramNames) {
        super(services, messages, hologramNames);
    }

    /** The single {@code action} subcommand node the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(Commands.literal("action")
                .then(nameArgument("name")
                        .then(addNode())
                        .then(editNode("add_before", this::addBefore))
                        .then(editNode("add_after", this::addAfter))
                        .then(editNode("set", this::set))
                        .then(moveNode("move_up", this::moveUp))
                        .then(moveNode("move_down", this::moveDown))
                        .then(Commands.literal("list").executes(this::list))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(this::remove)))
                        .then(Commands.literal("clear").executes(this::clear))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> addNode() {
        return Commands.literal("add")
                .then(choiceArgument("trigger", TRIGGER_WORDS)
                        .then(choiceArgument("type", TYPE_WORDS)
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(this::add))));
    }

    /** A {@code <literal> <index> <trigger> <type> <value…>} node, for the index-relative action edits. */
    private LiteralArgumentBuilder<CommandSourceStack> editNode(String literal, Command<CommandSourceStack> exec) {
        return Commands.literal(literal)
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .then(choiceArgument("trigger", TRIGGER_WORDS)
                                .then(choiceArgument("type", TYPE_WORDS)
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(exec)))));
    }

    /** A {@code <literal> <index>} node, for the reorder edits. */
    private LiteralArgumentBuilder<CommandSourceStack> moveNode(String literal, Command<CommandSourceStack> exec) {
        return Commands.literal(literal)
                .then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(exec));
    }

    private int add(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ClickAction action = parseAction(sender, ctx);
        if (action == null) {
            return 0;
        }
        services.addAction().add(actor(ctx), nameArg(ctx), action);
        return Command.SINGLE_SUCCESS;
    }

    private int addBefore(CommandContext<CommandSourceStack> ctx) {
        return insertAt(ctx, false);
    }

    private int addAfter(CommandContext<CommandSourceStack> ctx) {
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

    private int set(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ClickAction action = parseAction(sender, ctx);
        if (action == null) {
            return 0;
        }
        services.setAction().set(actor(ctx), nameArg(ctx), ctx.getArgument("index", Integer.class), action);
        return Command.SINGLE_SUCCESS;
    }

    private int moveUp(CommandContext<CommandSourceStack> ctx) {
        return move(ctx, true);
    }

    private int moveDown(CommandContext<CommandSourceStack> ctx) {
        return move(ctx, false);
    }

    private int move(CommandContext<CommandSourceStack> ctx, boolean up) {
        services.moveAction().move(actor(ctx), nameArg(ctx), ctx.getArgument("index", Integer.class), up);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Parse the {@code trigger}/{@code type}/{@code value} args into a {@link ClickAction}, sending the matching
     * validation feedback and returning {@code null} when any part is invalid (or a {@code give hand} capture
     * failed). Shared by {@code add}, {@code add_before}/{@code add_after} and {@code set}.
     */
    private @Nullable ClickAction parseAction(CommandSender sender, CommandContext<CommandSourceStack> ctx) {
        String triggerWord = ctx.getArgument("trigger", String.class);
        ClickTrigger trigger = parseTrigger(triggerWord);
        if (trigger == null) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_ACTION_INVALID_TRIGGER, Map.of("trigger", triggerWord));
            return null;
        }
        String typeWord = ctx.getArgument("type", String.class);
        ClickActionType type = ClickActionValueCheck.parseType(typeWord).orElse(null);
        if (type == null) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_ACTION_INVALID_TYPE, Map.of("type", typeWord));
            return null;
        }
        String value = resolveValue(sender, type, ctx.getArgument("value", String.class));
        if (value == null) {
            return null; // the capture failed (empty hand) and its feedback was already sent
        }
        ClickActionValueCheck.Result check = ClickActionValueCheck.check(type, value);
        if (check instanceof ClickActionValueCheck.Result.Invalid invalid) {
            feedback.send(
                    sender,
                    HologramsMessageKey.HOLOGRAM_ACTION_INVALID_VALUE,
                    Map.of("value", value, "type", typeWord.toLowerCase(Locale.ROOT), "hint", invalid.hint()));
            return null;
        }
        return new ClickAction(trigger, type, value);
    }

    /**
     * Resolve the value to store: for a {@code give hand} the sender's held item is captured as a serialized
     * token (a clone with all its NBT), failing with feedback on an empty hand; every other case stores the raw
     * value as typed. Returns {@code null} when the capture failed, signalling the handler to stop.
     */
    private @Nullable String resolveValue(CommandSender sender, ClickActionType type, String rawValue) {
        if (type != ClickActionType.GIVE || !rawValue.strip().equalsIgnoreCase(HAND_KEYWORD)) {
            return rawValue;
        }
        if (!(sender instanceof Player player)) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_PLAYERS_ONLY, Map.of());
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_ACTION_GIVE_EMPTY_HAND, Map.of());
            return null;
        }
        return SerializedItems.encode(hand);
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        services.listActions().list(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        services.removeAction().remove(actor(ctx), nameArg(ctx), ctx.getArgument("index", Integer.class));
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        services.clearActions().clear(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    /** Map the operator's trigger word (left/right/any) to a {@link ClickTrigger}, or {@code null} when unknown. */
    static @Nullable ClickTrigger parseTrigger(String word) {
        return switch (word.strip().toLowerCase(Locale.ROOT)) {
            case "left" -> ClickTrigger.LEFT_CLICK;
            case "right" -> ClickTrigger.RIGHT_CLICK;
            case "any" -> ClickTrigger.ANY;
            default -> null;
        };
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }
}
