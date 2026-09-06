package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.DyeColor;
import org.bukkit.entity.Horse;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcTypeData;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /npc data <set|clear|list>} subcommands: edit the per-type metadata an NPC carries
 * (baby/size/charged/villager), validated against {@link NpcTypeData}. A known key with a valid value is stored,
 * a clear drops it, and a list prints the stored data. Collected here so the appearance handler stays focused
 * while keeping the single {@code /npc} literal intact.
 */
@NullMarked
final class NpcDataCommands extends NpcCommandSupport {

    /** The accepted {@code data set}/{@code data clear} keys, also the tab suggestions. */
    private static final List<String> DATA_KEYS = List.of(
            "baby",
            "size",
            "charged",
            "villager_type",
            "villager_profession",
            "villager_level",
            "horse_color",
            "horse_markings",
            "horse_style",
            "llama_variant",
            "sheep_color",
            "wolf_collar",
            "shulker_color",
            "shulker_peek",
            "panda_gene",
            "parrot_variant",
            "axolotl_variant",
            "fox_type",
            "rabbit_type",
            "goat_screaming",
            "allay_dancing",
            "piglin_dancing",
            "camel_dash",
            "bee_nectar",
            "vex_charging",
            "axolotl_playing_dead",
            "illager_celebrating",
            "wolf_sitting",
            "cat_sitting",
            "panda_eating",
            "fox_pose",
            "sniffer_state",
            "armadillo_state",
            "use_item",
            "shaking",
            "sheep_sheared",
            "bee_rolling",
            "bee_stung",
            "tropical_fish",
            "armor_stand_small",
            "armor_stand_arms",
            "armor_stand_no_baseplate",
            "armor_stand_marker",
            "armor_stand_head",
            "armor_stand_body",
            "armor_stand_left_arm",
            "armor_stand_right_arm",
            "armor_stand_left_leg",
            "armor_stand_right_leg",
            "interaction_width",
            "interaction_height",
            "block",
            "item",
            "text",
            "display_scale",
            "display_billboard",
            "text_background",
            "text_line_width",
            "display_translation",
            "wolf_variant",
            "chicken_variant",
            "cow_variant",
            "pig_variant",
            "cat_variant",
            "frog_variant");
    /** The boolean keys' suggested values, offered as tab completions for {@code baby}/{@code charged}. */
    private static final List<String> BOOLEAN_VALUES = List.of("true", "false");
    /** The cat-variant names, offered as tab completions for {@code cat_variant}. */
    private static final List<String> CAT_VARIANT_VALUES = List.of(
            "tabby",
            "black",
            "red",
            "siamese",
            "british_shorthair",
            "calico",
            "persian",
            "ragdoll",
            "white",
            "jellie",
            "all_black");
    /** The frog-variant names, offered as tab completions for {@code frog_variant}. */
    private static final List<String> FROG_VARIANT_VALUES = List.of("temperate", "warm", "cold");

    private static final List<String> FOX_POSE_VALUES = List.of("standing", "sitting", "sleeping", "crouching");

    private static final List<String> SNIFFER_STATE_VALUES =
            List.of("idling", "feeling_happy", "scenting", "sniffing", "searching", "digging", "rising");

    private static final List<String> ARMADILLO_STATE_VALUES = List.of("idle", "rolling", "scared", "unrolling");

    private static final List<String> USE_ITEM_VALUES = List.of("none", "main_hand", "off_hand");
    /** The wolf coat-variant names, offered as tab completions for {@code wolf_variant}. */
    private static final List<String> WOLF_VARIANT_VALUES =
            List.of("pale", "spotted", "snowy", "black", "ashen", "rusty", "woods", "chestnut", "striped");
    /** The chicken/cow/pig temperature-variant names, offered for {@code chicken_variant}/{@code cow_variant}/{@code pig_variant}. */
    private static final List<String> TEMPERATURE_VARIANT_VALUES = List.of("temperate", "warm", "cold");
    /** The horse coat-colour ids (0 to 6), offered as tab completions for {@code horse_color}. */
    private static final List<String> HORSE_COLOR_VALUES = List.of("0", "1", "2", "3", "4", "5", "6");
    /** The horse marking ids (0 to 4), offered as tab completions for {@code horse_markings}. */
    private static final List<String> HORSE_MARKINGS_VALUES = List.of("0", "1", "2", "3", "4");
    /** The horse style names (the named alias of the markings), offered as tab completions for {@code horse_style}. */
    private static final List<String> HORSE_STYLE_VALUES = Arrays.stream(Horse.Style.values())
            .map(style -> style.name().toLowerCase(Locale.ROOT))
            .toList();
    /** The llama coat-variant ids (0 to 3), offered as tab completions for {@code llama_variant}. */
    private static final List<String> LLAMA_VARIANT_VALUES = List.of("0", "1", "2", "3");
    /** The sheep wool-colour names, offered as tab completions for {@code sheep_color} (a 0 to 15 id is also accepted). */
    private static final List<String> SHEEP_COLOR_VALUES = Arrays.stream(DyeColor.values())
            .map(color -> color.name().toLowerCase(Locale.ROOT))
            .toList();
    /** The shulker peek stops (0 closed … 100 open), offered as tab completions for {@code shulker_peek}. */
    private static final List<String> SHULKER_PEEK_VALUES = List.of("0", "25", "50", "75", "100");
    /** The panda gene ids (0 to 6), offered as tab completions for {@code panda_gene}. */
    private static final List<String> PANDA_GENE_VALUES = List.of("0", "1", "2", "3", "4", "5", "6");
    /** The predefined tropical-fish variant indices (0 to 21), offered as tab completions for {@code tropical_fish}. */
    private static final List<String> TROPICAL_FISH_VALUES = java.util.stream.IntStream.rangeClosed(0, 21)
            .mapToObj(Integer::toString)
            .toList();
    /** A sample {@code x,y,z} degrees triple, offered as a hint for the armor-stand pose keys. */
    private static final List<String> ARMOR_STAND_POSE_VALUES = List.of("0,0,0");
    /** Sample interaction hitbox sizes (blocks), offered as a hint for the interaction width/height keys. */
    private static final List<String> INTERACTION_SIZE_VALUES = List.of("1.0", "2.0");
    /** Sample block-data strings, offered as a hint for {@code block} (any block data is accepted). */
    private static final List<String> BLOCK_VALUES = List.of("stone", "oak_log", "glass");
    /** Sample item names, offered as a hint for {@code item} (any material name or {@code b64:} token is accepted). */
    private static final List<String> ITEM_VALUES = List.of("diamond", "golden_apple", "diamond_sword");
    /** Sample scales (a uniform float or an {@code x,y,z} triple), offered as a hint for {@code display_scale}. */
    private static final List<String> DISPLAY_SCALE_VALUES = List.of("1.0", "2.0", "0.5", "2,1,2");
    /** Sample translation offsets ({@code x,y,z} blocks), offered as a hint for {@code display_translation}. */
    private static final List<String> DISPLAY_TRANSLATION_VALUES = List.of("0,0,0", "0,0.5,0");
    /** The billboard modes, offered as tab completions for {@code display_billboard}. */
    private static final List<String> BILLBOARD_VALUES = List.of("fixed", "vertical", "horizontal", "center");
    /** Sample ARGB hex colours, offered as a hint for {@code text_background} (any {@code #aarrggbb}/{@code #rrggbb}). */
    private static final List<String> BACKGROUND_VALUES = List.of("#80000000", "#FF1E1E1E", "#00000000");
    /** Sample text-wrap widths (pixels), offered as a hint for {@code text_line_width} (default 200). */
    private static final List<String> LINE_WIDTH_VALUES = List.of("200", "100", "400");

    NpcDataCommands(
            NpcServices services,
            java.util.function.Supplier<? extends java.util.Collection<String>> npcNames,
            Messages messages) {
        super(services, npcNames, messages);
    }

    /** The {@code data} subcommand node the {@code /npc} literal attaches. */
    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("data")
                .then(Commands.literal("set")
                        .then(nameArgument()
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestDataKeys)
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .suggests(this::suggestDataValues)
                                                .executes(this::dataSet)))))
                .then(Commands.literal("clear")
                        .then(nameArgument()
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestDataKeys)
                                        .executes(this::dataClear))))
                .then(Commands.literal("list").then(nameArgument().executes(this::dataList)));
    }

    private int dataSet(CommandContext<CommandSourceStack> ctx) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        String key = ctx.getArgument("key", String.class).toLowerCase(Locale.ROOT);
        String value = value(ctx);
        if (!NpcTypeData.isKnownKey(key) || !NpcTypeData.isValidValue(key, value)) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_DATA, Map.of("key", key, "value", value));
            return 0;
        }
        services.setData().set(actor(ctx), nameArg(ctx), key, value);
        return Command.SINGLE_SUCCESS;
    }

    private int dataClear(CommandContext<CommandSourceStack> ctx) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        String key = ctx.getArgument("key", String.class).toLowerCase(Locale.ROOT);
        if (!NpcTypeData.isKnownKey(key)) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_DATA, Map.of("key", key, "value", ""));
            return 0;
        }
        services.setData().set(actor(ctx), nameArg(ctx), key, null);
        return Command.SINGLE_SUCCESS;
    }

    private int dataList(CommandContext<CommandSourceStack> ctx) {
        services.listData().list(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestDataKeys(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, DATA_KEYS);
    }

    /** Suggest the values for a key whose set is fixed ({@code baby}/{@code charged}, cat/frog); others take a free value. */
    private CompletableFuture<Suggestions> suggestDataValues(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String key = ctx.getArgument("key", String.class).toLowerCase(Locale.ROOT);
        return switch (key) {
            case "baby",
                    "charged",
                    "goat_screaming",
                    "allay_dancing",
                    "piglin_dancing",
                    "camel_dash",
                    "bee_nectar",
                    "vex_charging",
                    "axolotl_playing_dead",
                    "illager_celebrating",
                    "wolf_sitting",
                    "cat_sitting",
                    "panda_eating",
                    "shaking",
                    "sheep_sheared",
                    "bee_rolling",
                    "bee_stung",
                    "armor_stand_small",
                    "armor_stand_arms",
                    "armor_stand_no_baseplate",
                    "armor_stand_marker" -> suggest(builder, BOOLEAN_VALUES);
            case "fox_pose" -> suggest(builder, FOX_POSE_VALUES);
            case "sniffer_state" -> suggest(builder, SNIFFER_STATE_VALUES);
            case "armadillo_state" -> suggest(builder, ARMADILLO_STATE_VALUES);
            case "use_item" -> suggest(builder, USE_ITEM_VALUES);
            case "cat_variant" -> suggest(builder, CAT_VARIANT_VALUES);
            case "frog_variant" -> suggest(builder, FROG_VARIANT_VALUES);
            case "wolf_variant" -> suggest(builder, WOLF_VARIANT_VALUES);
            case "chicken_variant", "cow_variant", "pig_variant" -> suggest(builder, TEMPERATURE_VARIANT_VALUES);
            case "horse_color" -> suggest(builder, HORSE_COLOR_VALUES);
            case "horse_markings" -> suggest(builder, HORSE_MARKINGS_VALUES);
            case "horse_style" -> suggest(builder, HORSE_STYLE_VALUES);
            case "llama_variant" -> suggest(builder, LLAMA_VARIANT_VALUES);
            case "sheep_color", "wolf_collar", "shulker_color" -> suggest(builder, SHEEP_COLOR_VALUES);
            case "shulker_peek" -> suggest(builder, SHULKER_PEEK_VALUES);
            case "panda_gene" -> suggest(builder, PANDA_GENE_VALUES);
            case "tropical_fish" -> suggest(builder, TROPICAL_FISH_VALUES);
            case "armor_stand_head",
                    "armor_stand_body",
                    "armor_stand_left_arm",
                    "armor_stand_right_arm",
                    "armor_stand_left_leg",
                    "armor_stand_right_leg" -> suggest(builder, ARMOR_STAND_POSE_VALUES);
            case "interaction_width", "interaction_height" -> suggest(builder, INTERACTION_SIZE_VALUES);
            case "block" -> suggest(builder, BLOCK_VALUES);
            case "item" -> suggest(builder, ITEM_VALUES);
            case "display_scale" -> suggest(builder, DISPLAY_SCALE_VALUES);
            case "display_translation" -> suggest(builder, DISPLAY_TRANSLATION_VALUES);
            case "display_billboard" -> suggest(builder, BILLBOARD_VALUES);
            case "text_background" -> suggest(builder, BACKGROUND_VALUES);
            case "text_line_width" -> suggest(builder, LINE_WIDTH_VALUES);
            default -> builder.buildFuture();
        };
    }
}
