package com.uxplima.uxmessentials.persistence.npc;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcAppearance;
import com.uxplima.uxmessentials.npc.domain.NpcBehavior;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.NpcRecord;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between an {@code npc} row and the domain {@link Npc}. The world uuid is stored as
 * its canonical 36-character text and the creation time as epoch milliseconds, so the column shape is identical
 * on every backend. The skin columns are nullable: a NULL {@code skin_texture} reads back as no skin (the
 * default Steve fake player), and a present texture rebuilds an {@link NpcSkin} carrying its (possibly NULL)
 * signature. The {@code click_command} column is likewise nullable. A NULL means clicking the NPC does
 * nothing. The {@code look_at_player} column is a SMALLINT 0/1 read back as a boolean (whether the NPC rotates
 * to face nearby viewers). Equipment is now stored as an opaque per-slot token (either a legacy material name or
 * a serialized full-item payload) in the V45 TEXT columns ({@code equip_<slot>_b64}); the V40 VARCHAR columns
 * ({@code equip_<slot>}) are kept for backward compatibility, so a save writes the new column and a read takes
 * the new column first and falls back to the old one when it is NULL: an NPC stored before V45 keeps its gear.
 * A slot with both columns NULL is empty. {@code glowing} is a SMALLINT 0/1 and {@code glow_color} the optional
 * outline colour name. The {@code entity_type} column is the uppercase Bukkit {@code EntityType} name the NPC
 * renders as ({@code PLAYER} by default, the fake-player path), NOT NULL so an older row reads back as a player.
 * The {@code pose} column is the uppercase pose name the NPC is frozen in ({@code STANDING} by default), NOT NULL
 * so an older row reads upright; {@code scale} is the size multiplier in a REAL column ({@code 1.0} by default,
 * narrowed to a float on save and widened back on read), NOT NULL so an older row reads back natural-sized.
 * The click-action chain lives in the child {@code npc_action} table and is passed in already ordered, each
 * row's {@code click_trigger}/{@code type} are the enum names and {@code value} the raw operator payload. The
 * per-entity-type appearance metadata likewise lives in the child {@code npc_type_data} table and is passed in
 * already keyed. Each row's {@code data_key}/{@code data_value} are the opaque key/value the domain carries
 * verbatim. This class is the single place that translation lives.
 */
final class NpcRows {

    private static final com.uxplima.uxmessentials.persistence.jooq.tables.Npc NPC =
            com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;

    private NpcRows() {}

    /** Rebuild a domain {@link Npc} from an {@code npc} row, its already-ordered action list and its type data. */
    static Npc toNpc(Record row, List<ClickAction> orderedActions, Map<String, String> typeData) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(NPC.WORLD)), row.get(NPC.WORLD_NAME));
        Position position = new Position(
                world, row.get(NPC.X), row.get(NPC.Y), row.get(NPC.Z), row.get(NPC.YAW), row.get(NPC.PITCH));
        NpcAppearance appearance = new NpcAppearance(
                skinOf(row.get(NPC.SKIN_TEXTURE), row.get(NPC.SKIN_SIGNATURE), row.get(NPC.SKIN_SLIM)),
                row.get(NPC.ENTITY_TYPE),
                equipmentOf(row),
                row.get(NPC.GLOWING) != 0,
                row.get(NPC.GLOW_COLOR),
                row.get(NPC.POSE),
                row.get(NPC.SCALE),
                typeData,
                row.get(NPC.DISPLAY_NAME),
                row.get(NPC.MIRROR_SKIN) != 0,
                row.get(NPC.COLLIDABLE) != 0,
                row.get(NPC.SHOW_IN_TAB) != 0,
                widen(row.get(NPC.VIEW_DISTANCE)),
                widen(row.get(NPC.TURN_DISTANCE)),
                row.get(NPC.ON_FIRE) != 0,
                row.get(NPC.INVISIBLE) != 0,
                row.get(NPC.SILENT) != 0);
        NpcBehavior behavior = new NpcBehavior(
                row.get(NPC.CLICK_COMMAND),
                row.get(NPC.LOOK_AT_PLAYER) != 0,
                orderedActions,
                row.get(NPC.INTERACTION_COOLDOWN_MILLIS));
        return new Npc(
                NpcName.of(row.get(NPC.NAME)),
                position,
                appearance,
                behavior,
                Instant.ofEpochMilli(row.get(NPC.CREATED_AT)),
                ownerOf(row.get(NPC.OWNER_UUID)));
    }

    /** Populate an {@link NpcRecord} from a domain {@link Npc} for an upsert. */
    static void apply(NpcRecord record, Npc npc) {
        Position location = npc.location();
        NpcSkin skin = npc.skin();
        Map<EquipmentSlot, String> equipment = npc.equipment();
        record.setName(npc.name().value())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setSkinTexture(skin == null ? null : skin.texture())
                .setSkinSignature(skin == null ? null : skin.signature())
                .setSkinSlim((short) (skin != null && skin.slim() ? 1 : 0))
                .setClickCommand(npc.clickCommand())
                .setLookAtPlayer((short) (npc.lookAtPlayer() ? 1 : 0))
                .setInteractionCooldownMillis(npc.interactionCooldownMillis())
                // The token (material name or serialized item) is written to the V45 TEXT columns; the V40
                // VARCHAR columns are left NULL on a save and only ever read for a pre-V45 row's gear.
                .setEquipMainhandB64(equipment.get(EquipmentSlot.MAINHAND))
                .setEquipOffhandB64(equipment.get(EquipmentSlot.OFFHAND))
                .setEquipHeadB64(equipment.get(EquipmentSlot.HEAD))
                .setEquipChestB64(equipment.get(EquipmentSlot.CHEST))
                .setEquipLegsB64(equipment.get(EquipmentSlot.LEGS))
                .setEquipFeetB64(equipment.get(EquipmentSlot.FEET))
                .setGlowing((short) (npc.glowing() ? 1 : 0))
                .setGlowColor(npc.glowColor())
                .setEntityType(npc.entityType())
                .setPose(npc.pose())
                // scale is a REAL column (jOOQ maps it to Float); the domain carries the wider double, narrowed
                // here for storage and widened back on read: the protocol's scale range fits a float exactly.
                .setScale((float) npc.scale())
                .setDisplayName(npc.displayName())
                .setMirrorSkin((short) (npc.mirrorSkin() ? 1 : 0))
                .setCollidable((short) (npc.collidable() ? 1 : 0))
                .setShowInTab((short) (npc.showInTab() ? 1 : 0))
                .setOnFire((short) (npc.onFire() ? 1 : 0))
                .setInvisible((short) (npc.invisible() ? 1 : 0))
                .setSilent((short) (npc.silent() ? 1 : 0))
                // The per-NPC distance overrides are nullable REAL columns (a NULL means "use the module default"),
                // narrowed from the domain's double exactly like scale; a distance fits a float without loss.
                .setViewDistance(narrow(npc.viewDistance()))
                .setTurnDistance(narrow(npc.turnDistance()))
                .setOwnerUuid(npc.owner() == null ? null : npc.owner().toString())
                .setCreatedAt(npc.createdAt().toEpochMilli());
    }

    /** Parse a stored owner uuid back to a {@link UUID}, or {@code null} for an absent or unparseable value. */
    private static @Nullable UUID ownerOf(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private static @Nullable NpcSkin skinOf(@Nullable String texture, @Nullable String signature, short slim) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        return new NpcSkin(texture, signature, slim != 0);
    }

    /** Widen a nullable REAL distance back to the domain's {@code Double}, preserving a NULL "use default". */
    private static @Nullable Double widen(@Nullable Float value) {
        return value == null ? null : value.doubleValue();
    }

    /** Narrow a nullable domain distance to the REAL column's {@code Float}, preserving a NULL "use default". */
    private static @Nullable Float narrow(@Nullable Double value) {
        return value == null ? null : value.floatValue();
    }

    /**
     * Read the six equipment slots into a slot-keyed map, skipping the empty slots. Each slot prefers its V45
     * {@code equip_<slot>_b64} token and falls back to the V40 {@code equip_<slot>} material name when the new
     * column is NULL, so an NPC stored before V45 keeps its gear.
     */
    private static Map<EquipmentSlot, String> equipmentOf(Record row) {
        Map<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);
        put(equipment, EquipmentSlot.MAINHAND, row.get(NPC.EQUIP_MAINHAND_B64), row.get(NPC.EQUIP_MAINHAND));
        put(equipment, EquipmentSlot.OFFHAND, row.get(NPC.EQUIP_OFFHAND_B64), row.get(NPC.EQUIP_OFFHAND));
        put(equipment, EquipmentSlot.HEAD, row.get(NPC.EQUIP_HEAD_B64), row.get(NPC.EQUIP_HEAD));
        put(equipment, EquipmentSlot.CHEST, row.get(NPC.EQUIP_CHEST_B64), row.get(NPC.EQUIP_CHEST));
        put(equipment, EquipmentSlot.LEGS, row.get(NPC.EQUIP_LEGS_B64), row.get(NPC.EQUIP_LEGS));
        put(equipment, EquipmentSlot.FEET, row.get(NPC.EQUIP_FEET_B64), row.get(NPC.EQUIP_FEET));
        return equipment;
    }

    /** Store {@code token} for {@code slot}, preferring the V45 value and falling back to the legacy one. */
    private static void put(
            Map<EquipmentSlot, String> equipment, EquipmentSlot slot, @Nullable String token, @Nullable String legacy) {
        String value = token != null && !token.isBlank() ? token : legacy;
        if (value != null && !value.isBlank()) {
            equipment.put(slot, value);
        }
    }

    /**
     * Build a domain {@link ClickAction} from a stored row's trigger/type/value, or {@code null} when the trigger
     * or type enum name no longer parses (a forward-incompatible row is skipped on load rather than crashing the
     * whole NPC set). The caller filters the nulls out.
     */
    static @Nullable ClickAction toAction(String trigger, String type, String value) {
        ClickTrigger clickTrigger = enumOrNull(ClickTrigger.class, trigger);
        ClickActionType actionType = enumOrNull(ClickActionType.class, type);
        if (clickTrigger == null || actionType == null) {
            return null;
        }
        return new ClickAction(clickTrigger, actionType, value);
    }

    private static <E extends Enum<E>> @Nullable E enumOrNull(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
