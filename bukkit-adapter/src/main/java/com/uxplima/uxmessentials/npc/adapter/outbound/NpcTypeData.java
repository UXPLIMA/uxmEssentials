package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Breedable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an NPC's per-entity-type appearance metadata to the right uxmLib packets and sends them to one viewer,
 * branching on the NPC's entity type so a value is only ever sent to a type that actually carries that metadata
 * field. This is the per-type support map kept out of {@link NpcViewSpawner}: the spawner sends pose/scale/glow,
 * this sends the type-specific data ({@code baby}, {@code size}, {@code charged}, {@code villager_*}).
 *
 * <p>The correctness invariant is that a metadata value is never sent for an entity type that has no such field,
 * and never through an accessor whose data index differs for that type, a size only to a slime/magma cube,
 * charged only to a creeper, the villager data only to a villager. The baby flag is the subtle one: the breeding
 * animals, the villager line, and the hoglin are real {@code AgeableMob} subclasses and take the {@code baby}
 * packet, but the zombie line, piglins, and zoglins extend {@code Monster} and carry their baby flag at a
 * different data index, so each routes to its own packet ({@code zombieBaby}/{@code piglinBaby}/{@code
 * zoglinBaby}); a piglin brute and every non-ageable type get no baby packet at all. This keeps a wrong index
 * from ever landing on an unrelated field. Each property is
 * fail-soft: an unsupported key for the type is skipped, an unparseable value is skipped, and nothing throws on
 * the render thread (the same discipline as the pose/equipment fail-soft path). A skipped property logs at debug
 * with context so a misconfiguration is diagnosable without flooding the per-tick reconcile.
 */
@NullMarked
public final class NpcTypeData {

    static final String KEY_BABY = "baby";
    static final String KEY_SIZE = "size";
    static final String KEY_CHARGED = "charged";
    static final String KEY_VILLAGER_TYPE = "villager_type";
    static final String KEY_VILLAGER_PROFESSION = "villager_profession";
    static final String KEY_VILLAGER_LEVEL = "villager_level";

    /** The default villager type when a villager NPC sets a profession/level but no explicit type. */
    private static final String DEFAULT_VILLAGER_TYPE = "plains";
    /** The default villager profession when a villager NPC sets a type/level but no explicit profession. */
    private static final String DEFAULT_VILLAGER_PROFESSION = "none";
    /** The default villager badge level (the lowest tier) when none is set; also the lowest the client renders. */
    private static final int DEFAULT_VILLAGER_LEVEL = 1;
    /** The highest villager badge tier the client renders; a higher value would not map to a real badge. */
    private static final int MAX_VILLAGER_LEVEL = 5;
    /** The lower bound of a usable slime size. */
    private static final int MIN_SIZE = 1;
    /** The upper bound a slime size is clamped to, so a hostile value cannot spawn an absurd render box. */
    private static final int MAX_SIZE = 16;

    /**
     * The zombie-line types: {@code Ageable} on the Bukkit side but {@code Monster} (not {@code AgeableMob}) on the
     * server side, so their baby flag lives at {@code Zombie.DATA_BABY_ID}, a different index than the ageable one.
     */
    private static final Set<EntityType> ZOMBIE_FAMILY = Set.of(
            EntityType.ZOMBIE,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.ZOMBIFIED_PIGLIN);

    private NpcTypeData() {}

    /**
     * Send {@code npc}'s supported type data to {@code viewer} for the fake entity {@code entityId}, branching on
     * the NPC's resolved Bukkit entity type. A type that does not resolve, or an NPC carrying no type data, sends
     * nothing. Every property is fail-soft: an unsupported or unparseable one is skipped (logged at debug), never
     * thrown.
     */
    static void apply(NpcPackets packets, Player viewer, int entityId, Npc npc, Logger log) {
        if (!npc.hasTypeData()) {
            return;
        }
        EntityType type = resolveType(npc.entityType());
        if (type == null) {
            return;
        }
        Map<String, String> data = npc.typeData();
        applyBaby(packets, viewer, entityId, type, data, npc, log);
        applySize(packets, viewer, entityId, type, data, npc, log);
        applyCharged(packets, viewer, entityId, type, data, npc, log);
        applyVillager(packets, viewer, entityId, type, data);
        NpcVariantData.apply(packets, viewer, entityId, type, data, npc, log);
    }

    private static void applyBaby(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_BABY);
        if (value == null) {
            return;
        }
        boolean baby = Boolean.parseBoolean(value.strip());
        // Route to the builder whose accessor index matches the type. The breeding-animal/villager/hoglin line are
        // real AgeableMob subclasses (Bukkit Breedable); the zombie line, the piglin, and the zoglin extend Monster
        // and carry their baby flag at their own index, so each takes its own packet. A piglin brute (Ageable but no
        // baby form) and every non-ageable type get nothing: a baby value never reaches a wrong field.
        if (isBreedable(type)) {
            packets.send(viewer, packets.baby(id, baby));
        } else if (ZOMBIE_FAMILY.contains(type)) {
            packets.send(viewer, packets.zombieBaby(id, baby));
        } else if (type == EntityType.PIGLIN) {
            packets.send(viewer, packets.piglinBaby(id, baby));
        } else if (type == EntityType.ZOGLIN) {
            packets.send(viewer, packets.zoglinBaby(id, baby));
        } else {
            skip(log, npc, KEY_BABY, type, "type has no baby form at a known data index");
        }
    }

    private static void applySize(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_SIZE);
        if (value == null) {
            return;
        }
        if (!isSlime(type)) {
            skip(log, npc, KEY_SIZE, type, "type is not a slime or magma cube");
            return;
        }
        Integer size = parseInt(value);
        if (size == null) {
            skip(log, npc, KEY_SIZE, type, "value is not an integer: " + value);
            return;
        }
        packets.send(viewer, packets.slimeSize(id, Math.clamp(size, MIN_SIZE, MAX_SIZE)));
    }

    private static void applyCharged(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_CHARGED);
        if (value == null) {
            return;
        }
        if (type != EntityType.CREEPER) {
            skip(log, npc, KEY_CHARGED, type, "type is not a creeper");
            return;
        }
        packets.send(viewer, packets.charged(id, Boolean.parseBoolean(value.strip())));
    }

    private static void applyVillager(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data) {
        boolean any = data.containsKey(KEY_VILLAGER_TYPE)
                || data.containsKey(KEY_VILLAGER_PROFESSION)
                || data.containsKey(KEY_VILLAGER_LEVEL);
        if (!any || type != EntityType.VILLAGER) {
            return;
        }
        String villagerType = data.getOrDefault(KEY_VILLAGER_TYPE, DEFAULT_VILLAGER_TYPE);
        String profession = data.getOrDefault(KEY_VILLAGER_PROFESSION, DEFAULT_VILLAGER_PROFESSION);
        Integer level = parseInt(data.get(KEY_VILLAGER_LEVEL));
        int badge =
                level == null ? DEFAULT_VILLAGER_LEVEL : Math.clamp(level, DEFAULT_VILLAGER_LEVEL, MAX_VILLAGER_LEVEL);
        packets.send(viewer, packets.villagerData(id, villagerType, profession, badge));
    }

    /** Whether {@code key} is one this adapter knows how to apply: the set the command validates against. */
    public static boolean isKnownKey(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case KEY_BABY, KEY_SIZE, KEY_CHARGED, KEY_VILLAGER_TYPE, KEY_VILLAGER_PROFESSION, KEY_VILLAGER_LEVEL ->
                true;
            default -> NpcVariantData.isKnownKey(key);
        };
    }

    /**
     * Whether {@code value} is a valid value for the (already-known) {@code key}: a boolean for {@code baby}/{@code
     * charged}, a positive integer for {@code size}/{@code villager_level}, and any non-blank name for the villager
     * type/profession (an unknown registry name is fail-soft at render, defaulted there). The command rejects an
     * invalid value with {@code NPC_INVALID_DATA} before it ever reaches the domain.
     */
    public static boolean isValidValue(String key, String value) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case KEY_BABY, KEY_CHARGED -> isBoolean(value);
            case KEY_SIZE, KEY_VILLAGER_LEVEL -> isPositiveInt(value);
            case KEY_VILLAGER_TYPE, KEY_VILLAGER_PROFESSION -> !value.isBlank();
            default -> NpcVariantData.isKnownKey(key) && NpcVariantData.isValidValue(key, value);
        };
    }

    private static boolean isBoolean(String value) {
        String trimmed = value.strip().toLowerCase(Locale.ROOT);
        return trimmed.equals("true") || trimmed.equals("false");
    }

    private static boolean isPositiveInt(String value) {
        Integer parsed = parseInt(value);
        return parsed != null && parsed > 0;
    }

    /**
     * Whether {@code type} is a breeding mob. The Bukkit {@code Breedable} marker, which is exactly the set of
     * server-side {@code AgeableMob} subclasses (the breeding animals, the villager line, the hoglin). Bukkit's
     * wider {@code Ageable} also covers the zombie line, piglins, and zoglins, but those extend {@code Monster} and
     * keep their baby flag at a different index, so {@code Breedable} is the precise gate for the {@code baby}
     * packet.
     */
    private static boolean isBreedable(EntityType type) {
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && Breedable.class.isAssignableFrom(entityClass);
    }

    private static boolean isSlime(EntityType type) {
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && Slime.class.isAssignableFrom(entityClass);
    }

    private static @Nullable EntityType resolveType(String entityTypeName) {
        try {
            return EntityType.valueOf(entityTypeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
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

    private static void skip(Logger log, Npc npc, String key, EntityType type, String reason) {
        log.debug("NPC {} skips type-data {} on a {} NPC ({})", npc.name().value(), key, type.name(), reason);
    }
}
