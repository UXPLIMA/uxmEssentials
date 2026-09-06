package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.SetNpcRange;
import com.uxplima.uxmessentials.npc.application.SetNpcState;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The remaining subcommands under {@code /npc} that the appearance/skin/data/action handlers do not own:
 * {@code moveto} (explicit coordinates), {@code displayname}, {@code cooldown}, {@code mirror}, {@code collidable},
 * {@code showintab}, {@code viewdistance}, {@code turndistance}, {@code state} ({@code on_fire|invisible|silent}),
 * and {@code skinslim}. Every {@code name} word completes against the current NPC names and every choice/boolean
 * argument suggests its values. Collected here so the root {@code /npc} command stays focused while keeping the
 * single literal intact (each contributes argument nodes, never a new literal).
 */
@NullMarked
final class NpcStateCommands extends NpcCommandSupport {

    /** The {@code state} names suggested for {@code /npc state <name> <state>}. */
    private static final List<String> STATE_WORDS = List.of("on_fire", "invisible", "silent");
    /** The keyword that resets a per-NPC distance or cooldown override to the module default. */
    private static final String DEFAULT_KEYWORD = "default";
    /** Distance sentinels for {@code /npc viewdistance|turndistance}: never visible, or visible from any range. */
    private static final String NOT_VISIBLE_KEYWORD = "not_visible";

    private static final String ALWAYS_VISIBLE_KEYWORD = "always_visible";
    /** The block range that stands in for "always visible", far beyond any practical view distance, still finite. */
    private static final double ALWAYS_VISIBLE_BLOCKS = 100_000.0;

    NpcStateCommands(NpcServices services, Supplier<? extends Collection<String>> npcNames, Messages messages) {
        super(services, npcNames, messages);
    }

    /** The state subcommand nodes the {@code /npc} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                moveToNode(),
                displayNameNode(),
                cooldownNode(),
                bool("mirror", this::mirror),
                bool("collidable", this::collidable),
                bool("showintab", this::showInTab),
                distanceNode("viewdistance", SetNpcRange.Kind.VIEW),
                distanceNode("turndistance", SetNpcRange.Kind.TURN),
                stateNode(),
                bool("skinslim", this::skinSlim));
    }

    private LiteralArgumentBuilder<CommandSourceStack> moveToNode() {
        return Commands.literal("moveto")
                .then(nameArgument()
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> moveTo(ctx, 0f, 0f))
                                                .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> moveTo(ctx, yaw(ctx), 0f))
                                                        .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                                                                .executes(
                                                                        ctx -> moveTo(ctx, yaw(ctx), pitch(ctx)))))))));
    }

    private int moveTo(CommandContext<CommandSourceStack> ctx, float yaw, float pitch) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        double x = ctx.getArgument("x", Double.class);
        double y = ctx.getArgument("y", Double.class);
        double z = ctx.getArgument("z", Double.class);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_COORDS, Map.of("coords", x + " " + y + " " + z));
            return 0;
        }
        services.moveTo().moveTo(actor(ctx), nameArg(ctx), x, y, z, yaw, pitch);
        return Command.SINGLE_SUCCESS;
    }

    private static float yaw(CommandContext<CommandSourceStack> ctx) {
        return (float) (double) ctx.getArgument("yaw", Double.class);
    }

    private static float pitch(CommandContext<CommandSourceStack> ctx) {
        return (float) (double) ctx.getArgument("pitch", Double.class);
    }

    private LiteralArgumentBuilder<CommandSourceStack> displayNameNode() {
        return Commands.literal("displayname")
                .executes(ctx -> usage(ctx, "npc displayname", "<name> <text>", "Set NPC display name"))
                .then(nameArgument()
                        .executes(ctx -> usage(ctx, "npc displayname", "<name> <text>", "Set NPC display name"))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::displayName)));
    }

    private int displayName(CommandContext<CommandSourceStack> ctx) {
        String text = ctx.getArgument("text", String.class).strip();
        String resolved = text.equalsIgnoreCase("none") || text.isBlank() ? null : text;
        services.displayName().setDisplayName(actor(ctx), nameArg(ctx), resolved);
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> cooldownNode() {
        return Commands.literal("cooldown")
                .then(nameArgument()
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        suggest(builder, List.of(DEFAULT_KEYWORD, "0", "500ms", "30s", "5min")))
                                .executes(this::cooldown)));
    }

    private int cooldown(CommandContext<CommandSourceStack> ctx) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        String word = ctx.getArgument("duration", String.class).strip();
        Long millis = word.equalsIgnoreCase(DEFAULT_KEYWORD) ? 0L : parseFriendlyMillis(word);
        if (millis == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_COOLDOWN, Map.of("cooldown", word));
            return 0;
        }
        services.cooldown().setCooldown(actor(ctx), nameArg(ctx), millis);
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> distanceNode(String literal, SetNpcRange.Kind kind) {
        return Commands.literal(literal)
                .then(nameArgument()
                        .then(Commands.argument("blocks", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggest(
                                        builder,
                                        List.of(
                                                DEFAULT_KEYWORD,
                                                NOT_VISIBLE_KEYWORD,
                                                ALWAYS_VISIBLE_KEYWORD,
                                                "16",
                                                "48",
                                                "64")))
                                .executes(ctx -> distance(ctx, kind))));
    }

    private int distance(CommandContext<CommandSourceStack> ctx, SetNpcRange.Kind kind) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        String word = ctx.getArgument("blocks", String.class).strip();
        if (word.equalsIgnoreCase(DEFAULT_KEYWORD)) {
            services.range().setRange(actor(ctx), nameArg(ctx), kind, null);
            return Command.SINGLE_SUCCESS;
        }
        Double sentinel = sentinelBlocks(word);
        Double blocks = sentinel != null ? sentinel : parseNonNegativeDouble(word);
        if (blocks == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_DISTANCE, Map.of("distance", word));
            return 0;
        }
        services.range().setRange(actor(ctx), nameArg(ctx), kind, blocks);
        return Command.SINGLE_SUCCESS;
    }

    /** Resolve a visibility sentinel to its block range (0 for {@code not_visible}, far for {@code always_visible}) or {@code null}. */
    static @org.jspecify.annotations.Nullable Double sentinelBlocks(String word) {
        if (word.equalsIgnoreCase(NOT_VISIBLE_KEYWORD)) {
            return 0.0;
        }
        if (word.equalsIgnoreCase(ALWAYS_VISIBLE_KEYWORD)) {
            return ALWAYS_VISIBLE_BLOCKS;
        }
        return null;
    }

    private LiteralArgumentBuilder<CommandSourceStack> stateNode() {
        return Commands.literal("state")
                .then(nameArgument()
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggest(builder, STATE_WORDS))
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .suggests((ctx, builder) -> suggest(builder, BOOLEAN_WORDS))
                                        .executes(this::state))));
    }

    private int state(CommandContext<CommandSourceStack> ctx) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        String word = ctx.getArgument("state", String.class).strip().toUpperCase(Locale.ROOT);
        SetNpcState.Flag flag = parseFlag(word);
        if (flag == null) {
            feedback.send(
                    sender, NpcMessageKey.NPC_INVALID_STATE, Map.of("state", ctx.getArgument("state", String.class)));
            return 0;
        }
        services.state().setState(actor(ctx), nameArg(ctx), flag, ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    private int mirror(CommandContext<CommandSourceStack> ctx) {
        services.mirror().setMirror(actor(ctx), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    private int collidable(CommandContext<CommandSourceStack> ctx) {
        services.collidable().setCollidable(actor(ctx), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    private int showInTab(CommandContext<CommandSourceStack> ctx) {
        services.showInTab().setShowInTab(actor(ctx), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    private int skinSlim(CommandContext<CommandSourceStack> ctx) {
        services.skinSlim().setSlim(actor(ctx), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    /** Parse a state word to a {@link SetNpcState.Flag}, or {@code null} when it names no known state. */
    private static SetNpcState.@org.jspecify.annotations.Nullable Flag parseFlag(String upper) {
        try {
            return SetNpcState.Flag.valueOf(upper);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Parse a non-negative long, or {@code null} when the word is not a non-negative integer. */
    private static @org.jspecify.annotations.Nullable Long parseNonNegativeLong(String word) {
        try {
            long value = Long.parseLong(word);
            return value < 0 ? null : value;
        } catch (NumberFormatException notALong) {
            return null;
        }
    }

    /**
     * Parse a per-NPC interaction cooldown into milliseconds: a plain non-negative number is milliseconds, or a
     * number with a {@code ms}/{@code s}/{@code min}/{@code h} suffix (e.g. {@code 30s}, {@code 5min}, {@code 2h})
     * is scaled. Returns {@code null} when the word is not a non-negative duration. Package-private for the parsing
     * unit test.
     */
    static @org.jspecify.annotations.Nullable Long parseFriendlyMillis(String word) {
        String s = word.strip().toLowerCase(Locale.ROOT);
        int digits = 0;
        while (digits < s.length() && Character.isDigit(s.charAt(digits))) {
            digits++;
        }
        if (digits == 0) {
            return null;
        }
        Long number = parseNonNegativeLong(s.substring(0, digits));
        if (number == null) {
            return null;
        }
        long multiplier =
                switch (s.substring(digits).strip()) {
                    case "", "ms" -> 1L;
                    case "s", "sec", "secs", "seconds" -> 1_000L;
                    case "m", "min", "mins", "minutes" -> 60_000L;
                    case "h", "hr", "hrs", "hours" -> 3_600_000L;
                    default -> -1L;
                };
        return multiplier < 0 ? null : number * multiplier;
    }

    /** Parse a finite, non-negative double, or {@code null} when the word is not one. */
    private static @org.jspecify.annotations.Nullable Double parseNonNegativeDouble(String word) {
        try {
            double value = Double.parseDouble(word);
            return Double.isFinite(value) && value >= 0.0 ? value : null;
        } catch (NumberFormatException notADouble) {
            return null;
        }
    }
}
