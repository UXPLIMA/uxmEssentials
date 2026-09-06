package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption layer between an NPC's stored equipment token and a real {@link ItemStack}. An equipment
 * slot's value is an opaque {@code String} in {@code :core}; this is the only place the npc context interprets
 * it, so the domain never sees a Bukkit type.
 *
 * <p>A stored token is one of two shapes, kept unambiguous by a prefix:
 *
 * <ul>
 *   <li><b>Serialized item</b>. {@code b64:<base64>}, the full item (components, enchantments, custom name,
 *       lore, custom model data) as Paper's {@link ItemStack#serializeAsBytes()} bytes encoded to Base64. This
 *       is what {@link #serialize(ItemStack)} writes and the path a held or custom item takes.
 *   <li><b>Legacy material name</b>, a bare uppercase {@link Material} name ({@code DIAMOND_HELMET}), the shape
 *       the original V40 equipment columns hold. It carries no NBT, so it resolves to a plain
 *       {@code new ItemStack(material)}: the original behaviour, kept so existing NPCs keep their gear.
 * </ul>
 *
 * <p>{@link #resolve(String)} is fail-soft: a corrupt Base64 payload, an unknown material name, or a non-item
 * material (e.g. {@code AIR}) resolves to {@link Optional#empty()} so the caller skips that slot rather than
 * failing the whole spawn. It never throws on the render thread.
 */
@NullMarked
public final class EquipmentPayloads {

    /** The marker that distinguishes a serialized-item token from a legacy bare material name. */
    private static final String SERIALIZED_PREFIX = "b64:";

    private EquipmentPayloads() {}

    /** Serialize a full item into the {@code b64:<base64>} stored token, preserving all of its NBT. */
    public static String serialize(ItemStack item) {
        Objects.requireNonNull(item, "item");
        return SERIALIZED_PREFIX + Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    /** Whether {@code token} is a serialized-item payload (rather than a legacy bare material name). */
    public static boolean isSerialized(@Nullable String token) {
        return token != null && token.startsWith(SERIALIZED_PREFIX);
    }

    /**
     * Resolve a stored token to a real item, fail-soft: a {@code b64:} payload deserializes to its full item, a
     * legacy bare material name becomes a plain item, and a blank, corrupt or unresolvable token yields
     * {@link Optional#empty()} so the caller drops that slot. Never throws.
     */
    public static Optional<ItemStack> resolve(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        if (isSerialized(token)) {
            return deserialize(token.substring(SERIALIZED_PREFIX.length()));
        }
        return fromMaterialName(token);
    }

    private static Optional<ItemStack> deserialize(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return Optional.of(ItemStack.deserializeBytes(bytes));
        } catch (RuntimeException bad) {
            // A truncated, hand-edited or version-incompatible payload (bad base64 from the decoder, or NBT the
            // item deserializer rejects) must not crash the render. This codec's contract is to translate a bad
            // token into an absent item; the slot is dropped fail-soft and the spawn still goes out. The render
            // runs per viewer every reconcile tick, so logging here would flood. The "no item" result is the
            // signal, matching how an unknown material name already resolves to an empty slot.
            return Optional.empty();
        }
    }

    private static Optional<ItemStack> fromMaterialName(String name) {
        Material material = Material.matchMaterial(name.strip().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir() || !material.isItem()) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(material));
    }
}
