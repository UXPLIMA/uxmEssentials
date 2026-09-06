package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.EquipmentPayloads;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmlib.packet.npc.NpcPose;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The NPC appearance subcommands under {@code /npc}: {@code type <ENTITY_TYPE>} changes which living entity the
 * NPC renders as, {@code equip <slot> <material|hand|air>} dresses one slot, {@code glow <true|false> [color]}
 * toggles the glowing outline and its colour, {@code pose <pose>} freezes a body pose, and {@code scale <n>}
 * resizes it (clamped to the protocol's usable range). Collected here so the root {@code /npc} command stays
 * focused while keeping the single literal intact.
 */
@NullMarked
final class NpcAppearanceCommands extends NpcCommandSupport {

    private static final String HAND_KEYWORD = "hand";
    /** The smallest accepted scale: the lower bound of the protocol's usable {@code generic.scale} range. */
    private static final double MIN_SCALE = 0.0625;
    /** The largest accepted scale: the upper bound of the protocol's usable {@code generic.scale} range. */
    private static final double MAX_SCALE = 16.0;
    /** A sentinel the equip handler returns instead of a material when the word named no known item material. */
    private static final String INVALID_MATERIAL = "invalid-material";

    /** The equipment slot words suggested for {@code /npc equip <name> <slot>}. */
    private static final List<String> SLOT_WORDS = List.of("mainhand", "offhand", "head", "chest", "legs", "feet");
    /** The material keywords suggested for {@code /npc equip <name> <slot> <material>} alongside item names. */
    private static final List<String> MATERIAL_KEYWORDS = List.of("hand", "air", "none");
    /** The glow colour words suggested for {@code /npc glow <name> <bool> [color]}. */
    private static final List<String> COLOR_WORDS = List.of(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white");

    NpcAppearanceCommands(
            NpcServices services,
            java.util.function.Supplier<? extends java.util.Collection<String>> npcNames,
            Messages messages) {
        super(services, npcNames, messages);
    }

    /** The appearance subcommand nodes the {@code /npc} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(typeNode(), equipNode(), glowNode(), poseNode(), scaleNode());
    }

    private LiteralArgumentBuilder<CommandSourceStack> typeNode() {
        return Commands.literal("type")
                .then(nameArgument()
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(this::suggestEntityTypes)
                                .executes(this::type)));
    }

    private int type(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String word = ctx.getArgument("type", String.class);
        EntityType entityType = parseRenderableType(word);
        if (entityType == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_ENTITY_TYPE, Map.of("type", word));
            return 0;
        }
        services.type().setEntityType(actor(ctx), nameArg(ctx), entityType.name());
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestEntityTypes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (EntityType type : EntityType.values()) {
            if (isRenderableType(type) && type.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(type.name());
            }
        }
        return builder.buildFuture();
    }

    /** Suggest the {@code hand}/{@code air}/{@code none} keywords plus every item material name, prefix-filtered. */
    private CompletableFuture<Suggestions> suggestMaterials(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String keyword : MATERIAL_KEYWORDS) {
            if (keyword.startsWith(prefix)) {
                builder.suggest(keyword);
            }
        }
        for (Material material : Material.values()) {
            if (material.isItem() && material.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(material.name().toLowerCase(Locale.ROOT));
            }
        }
        return builder.buildFuture();
    }

    private LiteralArgumentBuilder<CommandSourceStack> equipNode() {
        return Commands.literal("equip")
                .then(nameArgument()
                        .then(Commands.literal("clear").executes(this::equipClear))
                        .then(Commands.literal("list").executes(this::equipList))
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggest(builder, SLOT_WORDS))
                                .then(Commands.argument("material", StringArgumentType.word())
                                        .suggests(this::suggestMaterials)
                                        .executes(this::equip))));
    }

    private int equipClear(CommandContext<CommandSourceStack> ctx) {
        services.equip().clearAll(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int equipList(CommandContext<CommandSourceStack> ctx) {
        services.listEquip().list(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int equip(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<EquipmentSlot> slot = EquipmentSlot.parse(ctx.getArgument("slot", String.class));
        if (slot.isEmpty()) {
            feedback.send(
                    sender, NpcMessageKey.NPC_INVALID_SLOT, Map.of("slot", ctx.getArgument("slot", String.class)));
            return 0;
        }
        String material = resolveMaterial(sender, ctx.getArgument("material", String.class));
        if (INVALID_MATERIAL.equals(material)) {
            return 0; // the invalid-material feedback was already sent
        }
        services.equip().setEquipment(actor(ctx), nameArg(ctx), slot.get(), material);
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> glowNode() {
        return Commands.literal("glow")
                .then(nameArgument()
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .suggests((ctx, builder) -> suggest(builder, BOOLEAN_WORDS))
                                .executes(this::glow)
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggest(builder, COLOR_WORDS))
                                        .executes(this::glow))));
    }

    private int glow(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        boolean glowing = ctx.getArgument("value", Boolean.class);
        String color = colorArg(ctx);
        if (glowing && color != null && !isKnownColor(color)) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_COLOR, Map.of("color", color));
            return 0;
        }
        services.glow().setGlowing(actor(ctx), nameArg(ctx), glowing, color == null ? "" : color);
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> poseNode() {
        return Commands.literal("pose")
                .then(nameArgument()
                        .then(Commands.argument("pose", StringArgumentType.word())
                                .suggests(this::suggestPoses)
                                .executes(this::pose)));
    }

    private int pose(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String word = ctx.getArgument("pose", String.class);
        NpcPose pose = parsePose(word);
        if (pose == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_POSE, Map.of("pose", word));
            return 0;
        }
        services.pose().setPose(actor(ctx), nameArg(ctx), pose.name());
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> scaleNode() {
        return Commands.literal("scale")
                .then(nameArgument()
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(this::scale)));
    }

    private int scale(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double value = ctx.getArgument("value", Double.class);
        if (!Double.isFinite(value) || value < MIN_SCALE || value > MAX_SCALE) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_SCALE, Map.of("scale", Double.toString(value)));
            return 0;
        }
        services.scale().setScale(actor(ctx), nameArg(ctx), value);
        return Command.SINGLE_SUCCESS;
    }

    /** Parse the pose word case-insensitively to an {@link NpcPose}, or {@code null} when it names no known pose. */
    private static @Nullable NpcPose parsePose(String word) {
        try {
            return NpcPose.valueOf(word.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private CompletableFuture<Suggestions> suggestPoses(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (NpcPose pose : NpcPose.values()) {
            if (pose.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(pose.name());
            }
        }
        return builder.buildFuture();
    }

    /**
     * Resolve the equip word into the token to store: {@code air}/{@code none} clears the slot (empty string),
     * {@code hand} captures the FULL item the sender holds. A single-quantity clone serialized with all its NBT
     * (enchantments, name, lore, custom model data), or empty when their hand is empty, and any other word is a
     * bare Material name (human-readable, resolved to a plain item at render). Returns the {@link #INVALID_MATERIAL}
     * sentinel after sending feedback when the word names no known item material.
     */
    private String resolveMaterial(CommandSender sender, String word) {
        String normalized = word.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("air") || normalized.equals("none")) {
            return "";
        }
        if (normalized.equals(HAND_KEYWORD)) {
            if (!(sender instanceof Player player)) {
                feedback.send(sender, NpcMessageKey.NPC_PLAYERS_ONLY, Map.of());
                return INVALID_MATERIAL;
            }
            return handToken(player);
        }
        Material material = Material.matchMaterial(word.strip());
        if (material == null || !material.isItem()) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_MATERIAL, Map.of("material", word));
            return INVALID_MATERIAL;
        }
        return material.name();
    }

    /** Serialize the sender's full held item (one copy) into a stored token, or empty when the hand is empty. */
    private static String handToken(Player sender) {
        ItemStack hand = sender.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return "";
        }
        ItemStack one = hand.clone();
        one.setAmount(1); // an equipment slot shows a single item, never a stack count
        return EquipmentPayloads.serialize(one);
    }

    private static boolean isKnownColor(String word) {
        try {
            com.uxplima.uxmlib.packet.npc.NamedColor.valueOf(word.strip().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    /** The optional {@code color} word for {@code /npc glow}, or {@code null} when the form omits it. */
    private static @Nullable String colorArg(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument("color", String.class);
        } catch (IllegalArgumentException absent) {
            return null;
        }
    }
}
