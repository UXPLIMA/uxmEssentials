package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The by-name dynamic-registry appearance variants: a cat/wolf coat variant, a frog variant, and the chicken/cow/pig
 * temperature variants. Unlike the bounded
 * integer variants of {@link NpcVariantData}, these are data-driven registry values addressed by name, the lib
 * resolves the matching {@code Holder} off the live server's cat-/frog-variant registry per call and returns null
 * fail-soft when it cannot (the server is not yet up, or the name is unknown), which {@link #apply} drops without
 * sending. Kept in its own class so {@code NpcVariantData} stays focused and under the size limit; the same
 * support-map correctness invariant holds. A name reaches only the one Bukkit type that carries that field, an
 * unsupported key or unknown name is skipped fail-soft (logged at debug), never thrown on the render thread.
 */
@NullMarked
final class NpcNameVariantData {

    static final String KEY_CAT_VARIANT = "cat_variant";
    static final String KEY_FROG_VARIANT = "frog_variant";
    static final String KEY_WOLF_VARIANT = "wolf_variant";
    static final String KEY_CHICKEN_VARIANT = "chicken_variant";
    static final String KEY_COW_VARIANT = "cow_variant";
    static final String KEY_PIG_VARIANT = "pig_variant";

    /** The vanilla wolf coat variants (the {@code minecraft:wolf_variant} registry keys). */
    static final List<String> WOLF_VARIANTS =
            List.of("pale", "spotted", "snowy", "black", "ashen", "rusty", "woods", "chestnut", "striped");

    /** The vanilla temperature variants shared by chicken/cow/pig (the 1.21.5 biome-variant registries). */
    static final List<String> TEMPERATURE_VARIANTS = List.of("temperate", "warm", "cold");

    /** The vanilla cat variants, validated before the lib resolves the matching holder off the live registry. */
    static final List<String> CAT_VARIANTS = List.of(
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

    /** The vanilla frog variants, validated before the lib resolves the matching holder off the live registry. */
    static final List<String> FROG_VARIANTS = List.of("temperate", "warm", "cold");

    /**
     * The by-name dynamic-registry variants: each is one Bukkit type, the known-name set the command validates
     * against, and the lib method that resolves the holder off the live server. The lib returns null fail-soft when
     * it cannot resolve the holder (server not yet up, or an unknown name), which {@link #applyVariant} drops.
     */
    private static final List<NameVariant> NAME_VARIANTS = List.of(
            new NameVariant(KEY_CAT_VARIANT, EntityType.CAT, Set.copyOf(CAT_VARIANTS), NpcPackets::catVariant),
            new NameVariant(KEY_FROG_VARIANT, EntityType.FROG, Set.copyOf(FROG_VARIANTS), NpcPackets::frogVariant),
            new NameVariant(KEY_WOLF_VARIANT, EntityType.WOLF, Set.copyOf(WOLF_VARIANTS), NpcPackets::wolfVariant),
            new NameVariant(
                    KEY_CHICKEN_VARIANT,
                    EntityType.CHICKEN,
                    Set.copyOf(TEMPERATURE_VARIANTS),
                    NpcPackets::chickenVariant),
            new NameVariant(KEY_COW_VARIANT, EntityType.COW, Set.copyOf(TEMPERATURE_VARIANTS), NpcPackets::cowVariant),
            new NameVariant(KEY_PIG_VARIANT, EntityType.PIG, Set.copyOf(TEMPERATURE_VARIANTS), NpcPackets::pigVariant));

    private NpcNameVariantData() {}

    /**
     * Send {@code npc}'s supported name-variant data to {@code viewer} for the fake entity {@code id}, branching on
     * the resolved Bukkit {@code type}. Every property is fail-soft: an unsupported or unknown one is skipped
     * (logged at debug), never thrown; a null packet from the lib (its unresolved signal) is dropped, not sent.
     */
    static void apply(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        for (NameVariant variant : NAME_VARIANTS) {
            applyVariant(packets, viewer, id, type, data, npc, log, variant);
        }
    }

    private static void applyVariant(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            NameVariant variant) {
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
        String name = value.strip().toLowerCase(Locale.ROOT);
        if (!variant.names().contains(name)) {
            skip(log, npc, variant.key(), type, "value is not a known variant: " + value);
            return;
        }
        // The lib resolves the dynamic-registry holder off the live server and returns null fail-soft when it
        // cannot (server not yet up, or the registry has no such entry); a null packet is dropped, never sent.
        Object packet = variant.send().build(packets, id, name);
        if (packet != null) {
            packets.send(viewer, packet);
        }
    }

    /** Whether {@code key} is one of the name-variant keys this class applies: the set the command validates against. */
    static boolean isKnownKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return byKey(lower) != null;
    }

    /**
     * Whether {@code value} is a valid name for the (already-known) name-variant {@code key}: a vanilla cat variant
     * for {@code cat_variant} and a vanilla frog variant for {@code frog_variant}, matched case-insensitively. The
     * lib re-resolves the holder off the live registry at render time, so this is the command-time name gate.
     */
    static boolean isValidValue(String key, String value) {
        NameVariant variant = byKey(key.toLowerCase(Locale.ROOT));
        return variant != null && variant.names().contains(value.strip().toLowerCase(Locale.ROOT));
    }

    /** The name-variant for {@code key} (already lower-cased), or {@code null} when none matches. */
    private static @Nullable NameVariant byKey(String key) {
        for (NameVariant variant : NAME_VARIANTS) {
            if (variant.key().equals(key)) {
                return variant;
            }
        }
        return null;
    }

    private static void skip(Logger log, Npc npc, String key, EntityType type, String reason) {
        log.debug("NPC {} skips variant-data {} on a {} NPC ({})", npc.name().value(), key, type.name(), reason);
    }

    /** A by-name dynamic-registry variant: its key, the one type that carries it, its known names, and its packet. */
    private record NameVariant(String key, EntityType type, Set<String> names, NameVariantSender send) {}

    /**
     * A send hook bound to a lib {@code (entityId, String)} variant method via a method reference. The build may
     * return null (the lib's fail-soft signal when the live registry cannot resolve the name), so {@code build} is
     * nullable and {@link #applyVariant} drops a null packet rather than sending it.
     */
    @FunctionalInterface
    private interface NameVariantSender {
        @Nullable Object build(NpcPackets packets, int entityId, String name);
    }
}
