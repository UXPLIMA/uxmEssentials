package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.shared.adapter.outbound.miniplaceholders.MiniPlaceholdersSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.npc.ArmorStandPart;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The per-entity-type appearance variants beyond the baby/size/charged/villager core of {@link NpcTypeData}: a
 * horse's coat colour and markings, a llama/parrot/axolotl coat variant, a fox/rabbit type, a sheep/wolf/shulker
 * colour, a shulker's peek, a panda's gene, a tropical fish's variant, the goat/allay/piglin/camel/bee/vex state
 * flags, an armor stand's client flags and six poses, an interaction entity's hitbox size, and a display entity's
 * content (a block/item/text display's shown block/item/text, a display's scale/billboard/translation, and a text
 * display's background colour and line width: the text resolved per viewer). Kept in its own class so
 * {@code NpcTypeData} stays focused; the same support-map correctness invariant holds. A value is sent only to the
 * one Bukkit type that carries that field, and an unsupported key or unparseable value is skipped fail-soft (logged
 * at debug), never thrown on the render thread.
 *
 * <p>Most variants are a bounded integer on one type, so a small {@link IntVariant} table drives them uniformly; the
 * dye-coloured ones (sheep wool, wolf collar, shulker shell) share a {@link ColorVariant} table that accepts a
 * {@link DyeColor} name or the raw 0 to 15 id alike; and the boolean states (goat screaming, allay/piglin dancing, camel
 * dash) share a {@link BoolVariant} table. The horse is the one special case: it packs a colour and a marking into
 * one packet (with {@code horse_style} a friendly {@link Horse.Style}-named alias for the marking id). The
 * by-name dynamic-registry variants (cat, frog) live in {@link NpcNameVariantData}; this class delegates the
 * known-key, validity, and apply paths to it so the two stay focused.
 */
@NullMarked
final class NpcVariantData {

    static final String KEY_HORSE_COLOR = "horse_color";
    static final String KEY_HORSE_MARKINGS = "horse_markings";
    static final String KEY_HORSE_STYLE = "horse_style";
    static final String KEY_LLAMA_VARIANT = "llama_variant";
    static final String KEY_SHEEP_COLOR = "sheep_color";
    static final String KEY_WOLF_COLLAR = "wolf_collar";
    static final String KEY_SHULKER_COLOR = "shulker_color";
    static final String KEY_SHULKER_PEEK = "shulker_peek";
    static final String KEY_PANDA_GENE = "panda_gene";
    static final String KEY_PARROT_VARIANT = "parrot_variant";
    static final String KEY_AXOLOTL_VARIANT = "axolotl_variant";
    static final String KEY_FOX_TYPE = "fox_type";
    static final String KEY_RABBIT_TYPE = "rabbit_type";
    static final String KEY_GOAT_SCREAMING = "goat_screaming";
    static final String KEY_ALLAY_DANCING = "allay_dancing";
    static final String KEY_PIGLIN_DANCING = "piglin_dancing";
    static final String KEY_CAMEL_DASH = "camel_dash";
    static final String KEY_BEE_NECTAR = "bee_nectar";
    static final String KEY_VEX_CHARGING = "vex_charging";
    static final String KEY_TROPICAL_FISH = "tropical_fish";
    static final String KEY_AS_SMALL = "armor_stand_small";
    static final String KEY_AS_ARMS = "armor_stand_arms";
    static final String KEY_AS_NO_BASEPLATE = "armor_stand_no_baseplate";
    static final String KEY_AS_MARKER = "armor_stand_marker";
    static final String KEY_AS_HEAD = "armor_stand_head";
    static final String KEY_AS_BODY = "armor_stand_body";
    static final String KEY_AS_LEFT_ARM = "armor_stand_left_arm";
    static final String KEY_AS_RIGHT_ARM = "armor_stand_right_arm";
    static final String KEY_AS_LEFT_LEG = "armor_stand_left_leg";
    static final String KEY_AS_RIGHT_LEG = "armor_stand_right_leg";
    static final String KEY_INTERACTION_WIDTH = "interaction_width";
    static final String KEY_INTERACTION_HEIGHT = "interaction_height";
    static final String KEY_BLOCK = "block";
    static final String KEY_ITEM = "item";
    static final String KEY_TEXT = "text";
    static final String KEY_DISPLAY_SCALE = "display_scale";
    static final String KEY_DISPLAY_BILLBOARD = "display_billboard";
    static final String KEY_TEXT_BACKGROUND = "text_background";
    static final String KEY_TEXT_LINE_WIDTH = "text_line_width";
    static final String KEY_DISPLAY_TRANSLATION = "display_translation";
    static final String KEY_AXOLOTL_PLAYING_DEAD = "axolotl_playing_dead";
    static final String KEY_ILLAGER_CELEBRATING = "illager_celebrating";
    static final String KEY_WOLF_SITTING = "wolf_sitting";
    static final String KEY_CAT_SITTING = "cat_sitting";
    static final String KEY_PANDA_EATING = "panda_eating";
    static final String KEY_FOX_POSE = "fox_pose";
    static final String KEY_SNIFFER_STATE = "sniffer_state";
    static final String KEY_ARMADILLO_STATE = "armadillo_state";
    static final String KEY_USE_ITEM = "use_item";
    static final String KEY_SHAKING = "shaking";
    static final String KEY_SHEEP_SHEARED = "sheep_sheared";
    static final String KEY_BEE_ROLLING = "bee_rolling";
    static final String KEY_BEE_STUNG = "bee_stung";

    /** The raider types a {@code Raider}-level attribute (celebrating) may render on. */
    private static final Set<EntityType> RAIDER_TYPES = Set.of(
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.EVOKER,
            EntityType.ILLUSIONER,
            EntityType.RAVAGER,
            EntityType.WITCH);

    /** The horse coat colours (0 to 6) and body markings (0 to 4); the two pack into one variant integer. */
    private static final int MAX_HORSE_COLOR = 6;

    private static final int MAX_HORSE_MARKINGS = 4;
    /** The highest wool colour id; a {@link DyeColor} name resolves to its wool id, which never exceeds this. */
    private static final int MAX_DYE_COLOR = 15;
    /** The killer (toast) rabbit's wire type id, valid even though it sits outside the 0 to 5 coat run. */
    private static final int RABBIT_KILLER = 99;
    /** The default interaction hitbox dimension (blocks) for a width/height that is absent or unparseable. */
    private static final float DEFAULT_INTERACTION_SIZE = 1.0f;
    /** A shulker shell opens from 0 (closed) to 100 (fully open). */
    private static final int MAX_SHULKER_PEEK = 100;
    /** The seven panda genes (0 to 6: normal, lazy, worried, playful, brown, weak, aggressive). */
    private static final int MAX_PANDA_GENE = 6;
    /** The highest index into the server's 22 predefined common tropical-fish variants. */
    private static final int MAX_TROPICAL_FISH_VARIANT = 21;

    /**
     * The single-key bounded-int variants: each is one Bukkit type, an inclusive max, and the lib method that
     * ships the value. The horse (two keys, one packet) and the sheep (name-or-id) are handled separately.
     */
    private static final List<IntVariant> INT_VARIANTS = List.of(
            new IntVariant(KEY_LLAMA_VARIANT, EntityType.LLAMA, 3, NpcPackets::llamaVariant),
            new IntVariant(KEY_PARROT_VARIANT, EntityType.PARROT, 4, NpcPackets::parrotVariant),
            new IntVariant(KEY_AXOLOTL_VARIANT, EntityType.AXOLOTL, 4, NpcPackets::axolotlVariant),
            new IntVariant(KEY_FOX_TYPE, EntityType.FOX, 1, NpcPackets::foxType),
            new IntVariant(KEY_SHULKER_PEEK, EntityType.SHULKER, MAX_SHULKER_PEEK, NpcPackets::shulkerPeek),
            new IntVariant(KEY_PANDA_GENE, EntityType.PANDA, MAX_PANDA_GENE, NpcPackets::pandaGene),
            new IntVariant(
                    KEY_TROPICAL_FISH,
                    EntityType.TROPICAL_FISH,
                    MAX_TROPICAL_FISH_VARIANT,
                    NpcPackets::tropicalFishVariant));

    /**
     * The single-key dye-colour variants: each is one Bukkit type and the lib method that ships a 0 to 15 colour id,
     * accepting a {@link DyeColor} name or the raw id alike. The wolf collar and shulker shell share the exact apply
     * and validation path; the sheep is handled separately ({@link #applySheep}) since its colour composes with the
     * sheared flag into one wool byte.
     */
    private static final List<ColorVariant> COLOR_VARIANTS = List.of(
            new ColorVariant(KEY_WOLF_COLLAR, EntityType.WOLF, NpcPackets::wolfCollar),
            new ColorVariant(KEY_SHULKER_COLOR, EntityType.SHULKER, NpcPackets::shulkerColor));

    /** The six armor-stand pose keys, each mapping a {@code "x,y,z"} degrees value onto one {@link ArmorStandPart}. */
    private static final List<PoseVariant> ARMOR_STAND_POSES = List.of(
            new PoseVariant(KEY_AS_HEAD, ArmorStandPart.HEAD),
            new PoseVariant(KEY_AS_BODY, ArmorStandPart.BODY),
            new PoseVariant(KEY_AS_LEFT_ARM, ArmorStandPart.LEFT_ARM),
            new PoseVariant(KEY_AS_RIGHT_ARM, ArmorStandPart.RIGHT_ARM),
            new PoseVariant(KEY_AS_LEFT_LEG, ArmorStandPart.LEFT_LEG),
            new PoseVariant(KEY_AS_RIGHT_LEG, ArmorStandPart.RIGHT_LEG));

    /** The single-key boolean state variants: each is one Bukkit type and the lib method that ships a true/false. */
    private static final List<BoolVariant> BOOL_VARIANTS = List.of(
            new BoolVariant(KEY_GOAT_SCREAMING, EntityType.GOAT, NpcPackets::goatScreaming),
            new BoolVariant(KEY_ALLAY_DANCING, EntityType.ALLAY, NpcPackets::allayDancing),
            new BoolVariant(KEY_PIGLIN_DANCING, EntityType.PIGLIN, NpcPackets::piglinDancing),
            new BoolVariant(KEY_CAMEL_DASH, EntityType.CAMEL, NpcPackets::camelDash),
            new BoolVariant(KEY_VEX_CHARGING, EntityType.VEX, NpcPackets::vexCharging),
            new BoolVariant(KEY_AXOLOTL_PLAYING_DEAD, EntityType.AXOLOTL, NpcPackets::axolotlPlayingDead),
            new BoolVariant(KEY_WOLF_SITTING, EntityType.WOLF, NpcPackets::tameableSitting),
            new BoolVariant(KEY_CAT_SITTING, EntityType.CAT, NpcPackets::tameableSitting),
            new BoolVariant(KEY_PANDA_EATING, EntityType.PANDA, NpcPackets::pandaEating));

    /** The fox pose values the {@code fox_pose} key accepts; each lights exactly one bit (or none for standing). */
    private static final Set<String> FOX_POSE_VALUES = Set.of("standing", "sitting", "sleeping", "crouching");

    /** The seven sniffer animation states the {@code sniffer_state} key accepts. */
    private static final Set<String> SNIFFER_STATES =
            Set.of("idling", "feeling_happy", "scenting", "sniffing", "searching", "digging", "rising");

    /** The four armadillo states the {@code armadillo_state} key accepts. */
    private static final Set<String> ARMADILLO_STATES = Set.of("idle", "rolling", "scared", "unrolling");

    /** The hand the {@code use_item} key may raise an item in (or {@code none} to lower it). */
    private static final Set<String> USE_ITEM_VALUES = Set.of("none", "main_hand", "off_hand");

    /** A frozen-tick count comfortably past the vanilla freeze threshold (140), so the shiver renders at once. */
    private static final int FREEZE_SHIVER_TICKS = 200;

    private NpcVariantData() {}

    /**
     * Send {@code npc}'s supported variant data to {@code viewer} for the fake entity {@code entityId}, branching
     * on the resolved Bukkit {@code type}. Every property is fail-soft: an unsupported or unparseable one is
     * skipped (logged at debug), never thrown.
     */
    static void apply(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        applyHorse(packets, viewer, id, type, data, npc, log);
        for (ColorVariant variant : COLOR_VARIANTS) {
            applyColorVariant(packets, viewer, id, type, data, npc, log, variant);
        }
        applySheep(packets, viewer, id, type, data, npc, log);
        applyBee(packets, viewer, id, type, data, npc, log);
        applyRabbit(packets, viewer, id, type, data, npc, log);
        for (IntVariant variant : INT_VARIANTS) {
            applyIntVariant(packets, viewer, id, type, data, npc, log, variant);
        }
        for (BoolVariant variant : BOOL_VARIANTS) {
            applyBoolVariant(packets, viewer, id, type, data, npc, log, variant);
        }
        applyArmorStand(packets, viewer, id, type, data, npc, log);
        for (PoseVariant pose : ARMOR_STAND_POSES) {
            applyArmorStandPose(packets, viewer, id, type, data, npc, log, pose);
        }
        applyInteraction(packets, viewer, id, type, data, npc, log);
        applyCelebrating(packets, viewer, id, type, data, npc, log);
        applyFoxPose(packets, viewer, id, type, data, npc, log);
        applyEnumState(
                packets,
                viewer,
                id,
                type,
                data,
                npc,
                log,
                KEY_SNIFFER_STATE,
                EntityType.SNIFFER,
                SNIFFER_STATES,
                NpcPackets::snifferState);
        applyEnumState(
                packets,
                viewer,
                id,
                type,
                data,
                npc,
                log,
                KEY_ARMADILLO_STATE,
                EntityType.ARMADILLO,
                ARMADILLO_STATES,
                NpcPackets::armadilloState);
        applyUseItem(packets, viewer, id, type, data, npc, log);
        applyShaking(packets, viewer, id, type, data, npc, log);
        applyBlockDisplay(packets, viewer, id, type, data, npc, log);
        applyItemDisplay(packets, viewer, id, type, data, npc, log);
        applyTextDisplay(packets, viewer, id, type, data, npc, log);
        applyDisplayScale(packets, viewer, id, type, data, npc, log);
        applyDisplayTranslation(packets, viewer, id, type, data, npc, log);
        applyDisplayBillboard(packets, viewer, id, type, data, npc, log);
        applyTextBackground(packets, viewer, id, type, data, npc, log);
        applyTextLineWidth(packets, viewer, id, type, data, npc, log);
        NpcNameVariantData.apply(packets, viewer, id, type, data, npc, log);
    }

    private static void applyHorse(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        if (!data.containsKey(KEY_HORSE_COLOR)
                && !data.containsKey(KEY_HORSE_MARKINGS)
                && !data.containsKey(KEY_HORSE_STYLE)) {
            return;
        }
        if (type != EntityType.HORSE) {
            skip(log, npc, KEY_HORSE_COLOR, type, "type is not a horse");
            return;
        }
        int color = clampInt(data.get(KEY_HORSE_COLOR), MAX_HORSE_COLOR);
        int markings = resolveMarkings(data);
        packets.send(viewer, packets.horseVariant(id, color, markings));
    }

    private static void applyColorVariant(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            ColorVariant variant) {
        String value = data.get(variant.key());
        if (value == null) {
            return;
        }
        if (type != variant.type()) {
            skip(
                    log,
                    npc,
                    variant.key(),
                    type,
                    "type is not a " + variant.type().name().toLowerCase(Locale.ROOT));
            return;
        }
        Integer color = parseColorId(value);
        if (color == null) {
            skip(log, npc, variant.key(), type, "value is not a dye colour or 0-15 id: " + value);
            return;
        }
        variant.send().accept(packets, viewer, id, color);
    }

    private static void applyRabbit(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_RABBIT_TYPE);
        if (value == null) {
            return;
        }
        if (type != EntityType.RABBIT) {
            skip(log, npc, KEY_RABBIT_TYPE, type, "type is not a rabbit");
            return;
        }
        Integer parsed = parseInt(value);
        if (parsed == null || !isRabbitType(parsed)) {
            skip(log, npc, KEY_RABBIT_TYPE, type, "value is not a 0-5 coat or 99 (killer): " + value);
            return;
        }
        packets.send(viewer, packets.rabbitType(id, parsed));
    }

    private static void applyIntVariant(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            IntVariant variant) {
        String value = data.get(variant.key());
        if (value == null) {
            return;
        }
        if (type != variant.type()) {
            skip(
                    log,
                    npc,
                    variant.key(),
                    type,
                    "type is not a " + variant.type().name().toLowerCase(Locale.ROOT));
            return;
        }
        Integer parsed = parseInt(value);
        if (parsed == null || parsed < 0 || parsed > variant.max()) {
            skip(log, npc, variant.key(), type, "value is out of range 0-" + variant.max() + ": " + value);
            return;
        }
        variant.send().accept(packets, viewer, id, parsed);
    }

    private static void applyBoolVariant(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            BoolVariant variant) {
        String value = data.get(variant.key());
        if (value == null) {
            return;
        }
        if (type != variant.type()) {
            skip(
                    log,
                    npc,
                    variant.key(),
                    type,
                    "type is not a " + variant.type().name().toLowerCase(Locale.ROOT));
            return;
        }
        Boolean parsed = parseBool(value);
        if (parsed == null) {
            skip(log, npc, variant.key(), type, "value is not true or false: " + value);
            return;
        }
        variant.send().accept(packets, viewer, id, parsed);
    }

    /**
     * Apply the armor-stand client flags: the four boolean keys compose into one {@code DATA_CLIENT_FLAGS} byte,
     * so unlike the {@link BoolVariant} table (one bit per packet) they go out as a single packet. A flag is on
     * only when its key is present and {@code true}; absent or {@code false} leaves it clear.
     */
    private static void applyArmorStand(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        if (!data.containsKey(KEY_AS_SMALL)
                && !data.containsKey(KEY_AS_ARMS)
                && !data.containsKey(KEY_AS_NO_BASEPLATE)
                && !data.containsKey(KEY_AS_MARKER)) {
            return;
        }
        if (type != EntityType.ARMOR_STAND) {
            skip(log, npc, KEY_AS_SMALL, type, "type is not an armor stand");
            return;
        }
        packets.send(
                viewer,
                packets.armorStandFlags(
                        id,
                        boolFlag(data, KEY_AS_SMALL),
                        boolFlag(data, KEY_AS_ARMS),
                        boolFlag(data, KEY_AS_NO_BASEPLATE),
                        boolFlag(data, KEY_AS_MARKER)));
    }

    /** A composed-flag read: {@code true} only when {@code key} is present and parses to {@code true}. */
    private static boolean boolFlag(Map<String, String> data, String key) {
        String value = data.get(key);
        return value != null && Boolean.TRUE.equals(parseBool(value));
    }

    /**
     * Apply one armor-stand pose: the stored {@code "x,y,z"} degrees set the {@link ArmorStandPart}'s rotation.
     * Each part is its own field, so each is an independent packet. Fail-soft on a wrong type or a value that is
     * not three comma-separated angles.
     */
    private static void applyArmorStandPose(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            PoseVariant pose) {
        String value = data.get(pose.key());
        if (value == null) {
            return;
        }
        if (type != EntityType.ARMOR_STAND) {
            skip(log, npc, pose.key(), type, "type is not an armor stand");
            return;
        }
        float[] angles = parseAngles(value);
        if (angles == null) {
            skip(log, npc, pose.key(), type, "value is not three comma-separated angles: " + value);
            return;
        }
        packets.send(viewer, packets.armorStandPose(id, pose.part(), angles[0], angles[1], angles[2]));
    }

    /**
     * Apply an interaction entity's hitbox size: the {@code interaction_width}/{@code interaction_height} keys set
     * the clickable box (defaulting a missing or unparseable dimension to 1 block) in one packet. An interaction
     * NPC is invisible, the hitbox is its whole point, so this is what makes it clickable. Sent only to the
     * {@code INTERACTION} type; fail-soft on a wrong type.
     */
    private static void applyInteraction(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        if (!data.containsKey(KEY_INTERACTION_WIDTH) && !data.containsKey(KEY_INTERACTION_HEIGHT)) {
            return;
        }
        if (type != EntityType.INTERACTION) {
            skip(log, npc, KEY_INTERACTION_WIDTH, type, "type is not an interaction entity");
            return;
        }
        float width = dimension(data.get(KEY_INTERACTION_WIDTH));
        float height = dimension(data.get(KEY_INTERACTION_HEIGHT));
        packets.send(viewer, packets.interactionSize(id, width, height));
    }

    /**
     * Apply a block-display entity's shown block: the {@code block} key (a BlockData string like {@code stone} or
     * {@code minecraft:oak_log[axis=y]}) sets it. Sent only to {@code BLOCK_DISPLAY}; an unparseable block-data
     * string is skipped fail-soft.
     */
    private static void applyBlockDisplay(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_BLOCK);
        if (value == null) {
            return;
        }
        if (type != EntityType.BLOCK_DISPLAY) {
            skip(log, npc, KEY_BLOCK, type, "type is not a block display");
            return;
        }
        BlockData block = parseBlockData(value);
        if (block == null) {
            skip(log, npc, KEY_BLOCK, type, "value is not valid block data: " + value);
            return;
        }
        packets.send(viewer, packets.blockDisplayState(id, block));
    }

    /**
     * Apply an item-display entity's shown item: the {@code item} key (a material name or a {@code b64:} item
     * token, the same grammar as equipment) sets it. Sent only to {@code ITEM_DISPLAY}; an unresolvable token is
     * skipped fail-soft.
     */
    private static void applyItemDisplay(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_ITEM);
        if (value == null) {
            return;
        }
        if (type != EntityType.ITEM_DISPLAY) {
            skip(log, npc, KEY_ITEM, type, "type is not an item display");
            return;
        }
        ItemStack item = EquipmentPayloads.resolve(value).orElse(null);
        if (item == null) {
            skip(log, npc, KEY_ITEM, type, "value is not a material or item token: " + value);
            return;
        }
        packets.send(viewer, packets.itemDisplayItem(id, item));
    }

    /**
     * Apply a raider's celebrating state (the {@code illager_celebrating} key), a {@code Raider}-level field, so it
     * reaches any raider type (pillager/vindicator/evoker/illusioner/ravager/witch). Fail-soft on a wrong type or
     * non-boolean value.
     */
    private static void applyCelebrating(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_ILLAGER_CELEBRATING);
        if (value == null) {
            return;
        }
        if (!RAIDER_TYPES.contains(type)) {
            skip(log, npc, KEY_ILLAGER_CELEBRATING, type, "type is not a raider");
            return;
        }
        Boolean celebrating = parseBool(value);
        if (celebrating == null) {
            skip(log, npc, KEY_ILLAGER_CELEBRATING, type, "value is not true or false: " + value);
            return;
        }
        packets.send(viewer, packets.raiderCelebrating(id, celebrating));
    }

    /**
     * Apply a sheep's wool: the {@code sheep_color} (a {@link DyeColor} name or 0 to 15 id) and the {@code sheep_sheared}
     * flag compose into the one wool byte, so a sheared sheep keeps its colour underneath. Either key alone is
     * enough; the other defaults (white, unsheared). Fail-soft on a bad value or non-sheep type.
     */
    private static void applySheep(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String colorValue = data.get(KEY_SHEEP_COLOR);
        String shearedValue = data.get(KEY_SHEEP_SHEARED);
        if (colorValue == null && shearedValue == null) {
            return;
        }
        if (type != EntityType.SHEEP) {
            skip(log, npc, colorValue != null ? KEY_SHEEP_COLOR : KEY_SHEEP_SHEARED, type, "type is not a sheep");
            return;
        }
        int color = 0;
        if (colorValue != null) {
            Integer parsed = parseColorId(colorValue);
            if (parsed == null) {
                skip(log, npc, KEY_SHEEP_COLOR, type, "value is not a dye colour or 0 to 15 id: " + colorValue);
                return;
            }
            color = parsed;
        }
        boolean sheared = false;
        if (shearedValue != null) {
            Boolean parsed = parseBool(shearedValue);
            if (parsed == null) {
                skip(log, npc, KEY_SHEEP_SHEARED, type, "value is not true or false: " + shearedValue);
                return;
            }
            sheared = parsed;
        }
        packets.send(viewer, packets.sheepWool(id, color, sheared));
    }

    /**
     * Apply a bee's state: the {@code bee_nectar}, {@code bee_rolling}, and {@code bee_stung} flags compose into the
     * one bee flags byte. Any subset of the keys is enough; absent ones default false. Fail-soft on a bad value or
     * non-bee type.
     */
    private static void applyBee(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String nectarValue = data.get(KEY_BEE_NECTAR);
        String rollingValue = data.get(KEY_BEE_ROLLING);
        String stungValue = data.get(KEY_BEE_STUNG);
        if (nectarValue == null && rollingValue == null && stungValue == null) {
            return;
        }
        if (type != EntityType.BEE) {
            skip(log, npc, KEY_BEE_NECTAR, type, "type is not a bee");
            return;
        }
        Boolean nectar = parseBeeFlag(nectarValue, KEY_BEE_NECTAR, type, npc, log);
        Boolean rolling = parseBeeFlag(rollingValue, KEY_BEE_ROLLING, type, npc, log);
        Boolean stung = parseBeeFlag(stungValue, KEY_BEE_STUNG, type, npc, log);
        if (nectar == null || rolling == null || stung == null) {
            return;
        }
        packets.send(viewer, packets.beeFlags(id, nectar, rolling, stung));
    }

    /** Parse one optional bee flag value: {@code false} when absent, the parsed bool when present, {@code null} on a bad value. */
    private static @Nullable Boolean parseBeeFlag(
            @Nullable String value, String key, EntityType type, Npc npc, Logger log) {
        if (value == null) {
            return Boolean.FALSE;
        }
        Boolean parsed = parseBool(value);
        if (parsed == null) {
            skip(log, npc, key, type, "value is not true or false: " + value);
        }
        return parsed;
    }

    /**
     * Apply a fox's pose (the {@code fox_pose} key): one of {@code standing}/{@code sitting}/{@code sleeping}/{@code
     * crouching}. The three poses are mutually exclusive bits in the one fox flags byte, so exactly one is lit.
     */
    private static void applyFoxPose(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_FOX_POSE);
        if (value == null) {
            return;
        }
        if (type != EntityType.FOX) {
            skip(log, npc, KEY_FOX_POSE, type, "type is not a fox");
            return;
        }
        String pose = value.toLowerCase(Locale.ROOT);
        if (!FOX_POSE_VALUES.contains(pose)) {
            skip(log, npc, KEY_FOX_POSE, type, "value is not a known fox pose: " + value);
            return;
        }
        packets.send(
                viewer,
                packets.foxFlags(id, pose.equals("sitting"), pose.equals("sleeping"), pose.equals("crouching")));
    }

    /**
     * Apply a named enum-state key (sniffer/armadillo): gate on the expected type, accept only a known state name,
     * then ship the lib packet (which returns {@code null} for a name the running server does not know, skipped).
     */
    private static void applyEnumState(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            String key,
            EntityType expected,
            Set<String> values,
            EnumStateSender sender) {
        String value = data.get(key);
        if (value == null) {
            return;
        }
        if (type != expected) {
            skip(log, npc, key, type, "type does not support this state");
            return;
        }
        String state = value.toLowerCase(Locale.ROOT);
        if (!values.contains(state)) {
            skip(log, npc, key, type, "value is not a known state: " + value);
            return;
        }
        Object packet = sender.build(packets, id, state);
        if (packet == null) {
            skip(log, npc, key, type, "server does not know this state: " + value);
            return;
        }
        packets.send(viewer, packet);
    }

    /**
     * Apply the item-use pose (the {@code use_item} key): {@code main_hand}/{@code off_hand} raises the held item
     * (a shield blocks, food/potion lifts), {@code none} lowers it. Only a living entity carries the flags byte.
     */
    private static void applyUseItem(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_USE_ITEM);
        if (value == null) {
            return;
        }
        if (!isLiving(type)) {
            skip(log, npc, KEY_USE_ITEM, type, "type is not a living entity");
            return;
        }
        String hand = value.toLowerCase(Locale.ROOT);
        if (!USE_ITEM_VALUES.contains(hand)) {
            skip(log, npc, KEY_USE_ITEM, type, "value is not none/main_hand/off_hand: " + value);
            return;
        }
        packets.send(viewer, packets.usingItem(id, !hand.equals("none"), hand.equals("off_hand")));
    }

    /** Apply the powder-snow shiver (the {@code shaking} key, a boolean) to a living entity via its frozen ticks. */
    private static void applyShaking(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_SHAKING);
        if (value == null) {
            return;
        }
        if (!isLiving(type)) {
            skip(log, npc, KEY_SHAKING, type, "type is not a living entity");
            return;
        }
        Boolean shaking = parseBool(value);
        if (shaking == null) {
            skip(log, npc, KEY_SHAKING, type, "value is not true or false: " + value);
            return;
        }
        packets.send(viewer, packets.frozenTicks(id, shaking ? FREEZE_SHIVER_TICKS : 0));
    }

    /** Whether {@code type} is a visible display entity (block/item/text) the shared scale/billboard apply to. */
    private static boolean isVisualDisplay(EntityType type) {
        return type == EntityType.BLOCK_DISPLAY || type == EntityType.ITEM_DISPLAY || type == EntityType.TEXT_DISPLAY;
    }

    /** Whether {@code type} is a living entity (carries the {@code DATA_LIVING_ENTITY_FLAGS} / freeze-overlay state). */
    private static boolean isLiving(EntityType type) {
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && LivingEntity.class.isAssignableFrom(entityClass);
    }

    /**
     * Apply a display entity's scale (the {@code display_scale} key): a single positive float scales uniformly,
     * or a positive {@code "x,y,z"} triple scales each axis. Fail-soft on a bad value or wrong type.
     */
    private static void applyDisplayScale(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_DISPLAY_SCALE);
        if (value == null) {
            return;
        }
        if (!isVisualDisplay(type)) {
            skip(log, npc, KEY_DISPLAY_SCALE, type, "type is not a block/item/text display");
            return;
        }
        if (value.indexOf(',') >= 0) {
            float[] axes = parseAngles(value);
            if (axes == null || axes[0] <= 0 || axes[1] <= 0 || axes[2] <= 0) {
                skip(log, npc, KEY_DISPLAY_SCALE, type, "value is not a positive x,y,z scale: " + value);
                return;
            }
            packets.send(viewer, packets.displayScale(id, axes[0], axes[1], axes[2]));
            return;
        }
        Float scale = parseFloat(value);
        if (scale == null || !Float.isFinite(scale) || scale <= 0) {
            skip(log, npc, KEY_DISPLAY_SCALE, type, "value is not a positive scale: " + value);
            return;
        }
        packets.send(viewer, packets.displayScale(id, scale));
    }

    /** Apply a display entity's translation offset (the {@code display_translation} key, {@code "x,y,z"} blocks). */
    private static void applyDisplayTranslation(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_DISPLAY_TRANSLATION);
        if (value == null) {
            return;
        }
        if (!isVisualDisplay(type)) {
            skip(log, npc, KEY_DISPLAY_TRANSLATION, type, "type is not a block/item/text display");
            return;
        }
        float[] offset = parseAngles(value);
        if (offset == null) {
            skip(log, npc, KEY_DISPLAY_TRANSLATION, type, "value is not three comma-separated offsets: " + value);
            return;
        }
        packets.send(viewer, packets.displayTranslation(id, offset[0], offset[1], offset[2]));
    }

    /** Apply a display entity's billboard constraint (the {@code display_billboard} key); fail-soft on a bad name. */
    private static void applyDisplayBillboard(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_DISPLAY_BILLBOARD);
        if (value == null) {
            return;
        }
        if (!isVisualDisplay(type)) {
            skip(log, npc, KEY_DISPLAY_BILLBOARD, type, "type is not a block/item/text display");
            return;
        }
        Byte constraint = parseBillboard(value);
        if (constraint == null) {
            skip(log, npc, KEY_DISPLAY_BILLBOARD, type, "value is not a billboard mode: " + value);
            return;
        }
        packets.send(viewer, packets.displayBillboard(id, constraint));
    }

    /** Apply a text-display entity's background colour (the {@code text_background} key); fail-soft on a bad colour. */
    private static void applyTextBackground(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_TEXT_BACKGROUND);
        if (value == null) {
            return;
        }
        if (type != EntityType.TEXT_DISPLAY) {
            skip(log, npc, KEY_TEXT_BACKGROUND, type, "type is not a text display");
            return;
        }
        Integer argb = parseArgb(value);
        if (argb == null) {
            skip(log, npc, KEY_TEXT_BACKGROUND, type, "value is not a hex ARGB colour: " + value);
            return;
        }
        packets.send(viewer, packets.textDisplayBackground(id, argb));
    }

    /** Apply a text-display entity's line width (the {@code text_line_width} key, wrap pixels); fail-soft on a bad value. */
    private static void applyTextLineWidth(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_TEXT_LINE_WIDTH);
        if (value == null) {
            return;
        }
        if (type != EntityType.TEXT_DISPLAY) {
            skip(log, npc, KEY_TEXT_LINE_WIDTH, type, "type is not a text display");
            return;
        }
        Integer width = parseInt(value);
        if (width == null || width <= 0) {
            skip(log, npc, KEY_TEXT_LINE_WIDTH, type, "value is not a positive line width: " + value);
            return;
        }
        packets.send(viewer, packets.textDisplayLineWidth(id, width));
    }

    /** A billboard name to its wire byte (FIXED 0, VERTICAL 1, HORIZONTAL 2, CENTER 3), or {@code null} when unknown. */
    private static @Nullable Byte parseBillboard(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "FIXED" -> (byte) 0;
            case "VERTICAL" -> (byte) 1;
            case "HORIZONTAL" -> (byte) 2;
            case "CENTER" -> (byte) 3;
            default -> null;
        };
    }

    /** Parse a hex colour ({@code #rrggbb}/{@code #aarrggbb}, optional {@code #}/{@code 0x}) to a packed ARGB int. */
    private static @Nullable Integer parseArgb(String value) {
        String hex = value.strip();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return null;
        }
        try {
            long bits = Long.parseLong(hex, 16);
            return hex.length() == 6 ? (0xFF000000 | (int) bits) : (int) bits;
        } catch (NumberFormatException notHex) {
            return null;
        }
    }

    /**
     * Apply a text-display entity's shown text: the {@code text} key (a MiniMessage string) is resolved <em>per
     * viewer</em>. Its {@code %papi%} tokens through that viewer's PlaceholderAPI bridge and its {@code <tag>}s
     * through the MiniPlaceholders global resolver (both no-ops when their plugin is absent, so the text is then
     * identical for everyone), then deserialised. Since this runs once per viewer at render, each viewer sees
     * their own values. Sent only to {@code TEXT_DISPLAY}; a MiniMessage parse error is skipped fail-soft.
     */
    private static void applyTextDisplay(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_TEXT);
        if (value == null) {
            return;
        }
        if (type != EntityType.TEXT_DISPLAY) {
            skip(log, npc, KEY_TEXT, type, "type is not a text display");
            return;
        }
        try {
            String resolved =
                    PlaceholderApiSupport.messageBridge(viewer.getUniqueId()).apply(value);
            packets.send(
                    viewer,
                    packets.textDisplayText(
                            id,
                            MiniMessage.miniMessage().deserialize(resolved, MiniPlaceholdersSupport.globalResolver())));
        } catch (RuntimeException badMiniMessage) {
            skip(log, npc, KEY_TEXT, type, "value is not valid MiniMessage: " + badMiniMessage);
        }
    }

    /** Parse a BlockData string (material or full state), or {@code null} when it is blank or invalid. */
    private static @Nullable BlockData parseBlockData(String value) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Bukkit.createBlockData(trimmed.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** A stored interaction dimension as a non-negative finite float, defaulting an absent/invalid one to 1 block. */
    private static float dimension(@Nullable String value) {
        Float parsed = value == null ? null : parseFloat(value);
        return parsed == null || !Float.isFinite(parsed) || parsed < 0 ? DEFAULT_INTERACTION_SIZE : parsed;
    }

    /** Whether {@code value} is a valid interaction dimension: a non-negative finite float. */
    private static boolean isDimension(String value) {
        Float parsed = parseFloat(value);
        return parsed != null && Float.isFinite(parsed) && parsed >= 0;
    }

    /** Parse a {@code "x,y,z"} triple of finite degree angles, or {@code null} when it is not three valid floats. */
    private static float @Nullable [] parseAngles(String value) {
        // Keep trailing empties (limit -1) so "1,2,3," is four parts and fails the count, not a silent three.
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        float[] angles = new float[3];
        for (int i = 0; i < 3; i++) {
            Float angle = parseFloat(parts[i]);
            if (angle == null || !Float.isFinite(angle)) {
                return null;
            }
            angles[i] = angle;
        }
        return angles;
    }

    private static @Nullable Float parseFloat(String value) {
        try {
            return Float.valueOf(value.strip());
        } catch (NumberFormatException notAFloat) {
            return null;
        }
    }

    /** Whether {@code key} is one of the variant keys this class applies: the set the command validates against. */
    static boolean isKnownKey(String key) {
        return KEYS.contains(key.toLowerCase(Locale.ROOT)) || NpcNameVariantData.isKnownKey(key);
    }

    /**
     * Whether {@code value} is valid for the (already-known) variant {@code key}: a 0 to max integer for the bounded
     * coats/types, a 0 to 5 coat or 99 for the rabbit, and a {@link DyeColor} name or a 0 to 15 id for the sheep colour.
     */
    static boolean isValidValue(String key, String value) {
        String lower = key.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case KEY_HORSE_COLOR -> isInRange(value, MAX_HORSE_COLOR);
            case KEY_HORSE_MARKINGS -> isInRange(value, MAX_HORSE_MARKINGS);
            case KEY_HORSE_STYLE -> parseStyle(value) != null;
            case KEY_SHEEP_COLOR, KEY_WOLF_COLLAR, KEY_SHULKER_COLOR -> parseColorId(value) != null;
            case KEY_GOAT_SCREAMING,
                    KEY_ALLAY_DANCING,
                    KEY_PIGLIN_DANCING,
                    KEY_CAMEL_DASH,
                    KEY_BEE_NECTAR,
                    KEY_VEX_CHARGING,
                    KEY_AXOLOTL_PLAYING_DEAD,
                    KEY_ILLAGER_CELEBRATING,
                    KEY_WOLF_SITTING,
                    KEY_CAT_SITTING,
                    KEY_PANDA_EATING,
                    KEY_SHEEP_SHEARED,
                    KEY_BEE_ROLLING,
                    KEY_BEE_STUNG,
                    KEY_AS_SMALL,
                    KEY_AS_ARMS,
                    KEY_AS_NO_BASEPLATE,
                    KEY_AS_MARKER -> parseBool(value) != null;
            case KEY_FOX_POSE -> FOX_POSE_VALUES.contains(value.toLowerCase(Locale.ROOT));
            case KEY_SNIFFER_STATE -> SNIFFER_STATES.contains(value.toLowerCase(Locale.ROOT));
            case KEY_ARMADILLO_STATE -> ARMADILLO_STATES.contains(value.toLowerCase(Locale.ROOT));
            case KEY_USE_ITEM -> USE_ITEM_VALUES.contains(value.toLowerCase(Locale.ROOT));
            case KEY_SHAKING -> parseBool(value) != null;
            case KEY_AS_HEAD, KEY_AS_BODY, KEY_AS_LEFT_ARM, KEY_AS_RIGHT_ARM, KEY_AS_LEFT_LEG, KEY_AS_RIGHT_LEG ->
                parseAngles(value) != null;
            case KEY_INTERACTION_WIDTH, KEY_INTERACTION_HEIGHT -> isDimension(value);
            // Block/item content is validated leniently (non-blank) so this stays Bukkit-free; the apply path
            // resolves the BlockData/item and skips fail-soft if it is unparseable.
            case KEY_BLOCK, KEY_ITEM, KEY_TEXT -> !value.isBlank();
            case KEY_DISPLAY_SCALE -> {
                if (value.indexOf(',') >= 0) {
                    float[] axes = parseAngles(value);
                    yield axes != null && axes[0] > 0 && axes[1] > 0 && axes[2] > 0;
                }
                Float scale = parseFloat(value);
                yield scale != null && Float.isFinite(scale) && scale > 0;
            }
            case KEY_DISPLAY_TRANSLATION -> parseAngles(value) != null;
            case KEY_DISPLAY_BILLBOARD -> parseBillboard(value) != null;
            case KEY_TEXT_BACKGROUND -> parseArgb(value) != null;
            case KEY_TEXT_LINE_WIDTH -> {
                Integer width = parseInt(value);
                yield width != null && width > 0;
            }
            case KEY_RABBIT_TYPE -> {
                Integer parsed = parseInt(value);
                yield parsed != null && isRabbitType(parsed);
            }
            default -> {
                if (NpcNameVariantData.isKnownKey(lower)) {
                    yield NpcNameVariantData.isValidValue(lower, value);
                }
                IntVariant variant = byKey(lower);
                yield variant != null && isInRange(value, variant.max());
            }
        };
    }

    private static boolean isInRange(String value, int max) {
        Integer parsed = parseInt(value);
        return parsed != null && parsed >= 0 && parsed <= max;
    }

    private static boolean isRabbitType(int value) {
        return (value >= 0 && value <= 5) || value == RABBIT_KILLER;
    }

    /** Clamp a stored int into {@code 0..max}, defaulting a missing or unparseable value to 0 (the first variant). */
    private static int clampInt(@Nullable String value, int max) {
        Integer parsed = parseInt(value);
        return parsed == null ? 0 : Math.clamp(parsed, 0, max);
    }

    /**
     * The horse body markings 0 to 4 to ship: a {@link Horse.Style} name ({@code horse_style}) when one is set (its
     * ordinal is the wire markings id), else the raw {@code horse_markings} integer. The style key is the friendly
     * alias for the same field, so when both are present the named style wins.
     */
    private static int resolveMarkings(Map<String, String> data) {
        Integer style = parseStyle(data.get(KEY_HORSE_STYLE));
        return style != null ? style : clampInt(data.get(KEY_HORSE_MARKINGS), MAX_HORSE_MARKINGS);
    }

    /** A {@link Horse.Style} name resolved to its 0 to 4 markings id, or {@code null} when absent or not a style name. */
    private static @Nullable Integer parseStyle(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return markingsOf(Horse.Style.valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAStyle) {
            return null;
        }
    }

    /** The wire markings id for a horse style, an explicit map (not the enum ordinal, which Error Prone flags). */
    private static int markingsOf(Horse.Style style) {
        return switch (style) {
            case NONE -> 0;
            case WHITE -> 1;
            case WHITEFIELD -> 2;
            case WHITE_DOTS -> 3;
            case BLACK_DOTS -> 4;
        };
    }

    /**
     * Resolve a sheep colour to its 0 to 15 wool id from either a {@link DyeColor} name (e.g. {@code red}) or a raw
     * id, or {@code null} when the value is neither: the fail-soft signal the apply and validation paths share.
     */
    private static @Nullable Integer parseColorId(String value) {
        String trimmed = value.strip();
        Integer raw = parseInt(trimmed);
        if (raw != null) {
            return raw >= 0 && raw <= MAX_DYE_COLOR ? raw : null;
        }
        try {
            return (int) DyeColor.valueOf(trimmed.toUpperCase(Locale.ROOT)).getWoolData();
        } catch (IllegalArgumentException notADyeColor) {
            return null;
        }
    }

    /** Parse a strict {@code true}/{@code false} (case-insensitive), or {@code null} for anything else. */
    private static @Nullable Boolean parseBool(String value) {
        String trimmed = value.strip().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static @Nullable Integer parseInt(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException notAnInt) {
            return null;
        }
    }

    private static @Nullable IntVariant byKey(String key) {
        for (IntVariant variant : INT_VARIANTS) {
            if (variant.key().equals(key)) {
                return variant;
            }
        }
        return null;
    }

    private static void skip(Logger log, Npc npc, String key, EntityType type, String reason) {
        log.debug("NPC {} skips variant-data {} on a {} NPC ({})", npc.name().value(), key, type.name(), reason);
    }

    /** A single-key bounded-int variant: its key, the one type that carries it, the inclusive max, and its packet. */
    private record IntVariant(String key, EntityType type, int max, IntVariantSender send) {}

    /** A single-key dye-colour variant: its key, the one type that carries it, and the lib packet it ships. */
    private record ColorVariant(String key, EntityType type, IntVariantSender send) {}

    /** A single-key boolean state variant: its key, the one type that carries it, and the lib packet it ships. */
    private record BoolVariant(String key, EntityType type, BoolVariantSender send) {}

    /** An armor-stand pose key bound to the {@link ArmorStandPart} it sets from a {@code "x,y,z"} degrees value. */
    private record PoseVariant(String key, ArmorStandPart part) {}

    /** A four-argument send hook bound to a lib {@code (entityId, boolean)} variant method via a method reference. */
    @FunctionalInterface
    private interface BoolVariantSender {
        Object build(NpcPackets packets, int entityId, boolean value);

        default void accept(NpcPackets packets, Player viewer, int entityId, boolean value) {
            packets.send(viewer, build(packets, entityId, value));
        }
    }

    /** A send hook bound to a lib {@code (entityId, String)} enum-state method that may return {@code null}. */
    @FunctionalInterface
    private interface EnumStateSender {
        @Nullable Object build(NpcPackets packets, int entityId, String state);
    }

    /** A four-argument send hook bound to a lib {@code (entityId, int)} variant method via a method reference. */
    @FunctionalInterface
    private interface IntVariantSender {
        Object build(NpcPackets packets, int entityId, int value);

        default void accept(NpcPackets packets, Player viewer, int entityId, int value) {
            packets.send(viewer, build(packets, entityId, value));
        }
    }

    /** The full set of variant keys, lower-case, for the known-key check (the table keys plus the special ones). */
    private static final Set<String> KEYS = Set.of(
            KEY_HORSE_COLOR,
            KEY_HORSE_MARKINGS,
            KEY_HORSE_STYLE,
            KEY_LLAMA_VARIANT,
            KEY_SHEEP_COLOR,
            KEY_WOLF_COLLAR,
            KEY_SHULKER_COLOR,
            KEY_SHULKER_PEEK,
            KEY_PANDA_GENE,
            KEY_PARROT_VARIANT,
            KEY_AXOLOTL_VARIANT,
            KEY_FOX_TYPE,
            KEY_RABBIT_TYPE,
            KEY_GOAT_SCREAMING,
            KEY_ALLAY_DANCING,
            KEY_PIGLIN_DANCING,
            KEY_CAMEL_DASH,
            KEY_BEE_NECTAR,
            KEY_VEX_CHARGING,
            KEY_AXOLOTL_PLAYING_DEAD,
            KEY_ILLAGER_CELEBRATING,
            KEY_WOLF_SITTING,
            KEY_CAT_SITTING,
            KEY_PANDA_EATING,
            KEY_FOX_POSE,
            KEY_SNIFFER_STATE,
            KEY_ARMADILLO_STATE,
            KEY_USE_ITEM,
            KEY_SHAKING,
            KEY_SHEEP_SHEARED,
            KEY_BEE_ROLLING,
            KEY_BEE_STUNG,
            KEY_TROPICAL_FISH,
            KEY_AS_SMALL,
            KEY_AS_ARMS,
            KEY_AS_NO_BASEPLATE,
            KEY_AS_MARKER,
            KEY_AS_HEAD,
            KEY_AS_BODY,
            KEY_AS_LEFT_ARM,
            KEY_AS_RIGHT_ARM,
            KEY_AS_LEFT_LEG,
            KEY_AS_RIGHT_LEG,
            KEY_INTERACTION_WIDTH,
            KEY_INTERACTION_HEIGHT,
            KEY_BLOCK,
            KEY_ITEM,
            KEY_TEXT,
            KEY_DISPLAY_SCALE,
            KEY_DISPLAY_BILLBOARD,
            KEY_TEXT_BACKGROUND,
            KEY_TEXT_LINE_WIDTH,
            KEY_DISPLAY_TRANSLATION);
}
