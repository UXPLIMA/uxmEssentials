package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.uxplima.uxmessentials.itemworld.domain.AttributeSpec;
import com.uxplima.uxmessentials.itemworld.domain.EnchantSpec;
import com.uxplima.uxmessentials.itemworld.domain.ItemQuery;
import org.jspecify.annotations.NullMarked;

/**
 * The anti-corruption boundary that resolves the itemworld domain's already-validated, normalised ids against
 * the live Paper registries: an {@link ItemQuery} to a {@link Material}, an {@link EnchantSpec} id to an
 * {@link Enchantment}, and an item-flag token to an {@link ItemFlag}. The domain owns the id <em>shape</em>
 * (lowercase {@code namespace:path}, grammar-checked); this resolver owns the final registry lookup, which is
 * the one remaining failure mode a well-formed-but-unknown id hits. Mapped by the caller to the matching
 * {@code ItemWorldError}/{@code MessageKey}.
 *
 * <p>Resolution is pure and side-effect-free: each method returns an {@link Optional} that is empty for an
 * unknown id rather than throwing, so a command renders the localized failure and stops. Only item-typed
 * materials pass {@link #material}. A non-item material (a block-only id) is rejected like an unknown item, so
 * {@code /give} and {@code /item} never hand the player an unobtainable stack.
 */
@NullMarked
public final class BukkitItemResolver {

    private BukkitItemResolver() {}

    /** The {@link Material} for {@code query}, present only when it resolves to an obtainable item. */
    public static Optional<Material> material(ItemQuery query) {
        NamespacedKey key = NamespacedKey.fromString(query.asKey());
        if (key == null) {
            return Optional.empty();
        }
        Material material = Registry.MATERIAL.get(key);
        if (material == null || !material.isItem()) {
            return Optional.empty();
        }
        return Optional.of(material);
    }

    /** The {@link Enchantment} for {@code spec}'s id, present only when it resolves in the registry. */
    public static Optional<Enchantment> enchantment(EnchantSpec spec) {
        NamespacedKey key = NamespacedKey.fromString(spec.enchantId());
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(key));
    }

    /** The {@link ItemFlag} for a case-insensitive token ({@code hide_enchants}), present only when it matches. */
    public static Optional<ItemFlag> itemFlag(String token) {
        String normalised = token.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(ItemFlag.valueOf(normalised));
        } catch (IllegalArgumentException unknownFlag) {
            return Optional.empty();
        }
    }

    /**
     * The {@link Enchantment} for a raw {@code /itemedit enchant} token, present only when it resolves. The
     * {@code /itemedit} editor needs the resolved enchant up front (for its vanilla max level) before the domain
     * {@link EnchantSpec} clamps, so it resolves the token directly here rather than through a pre-built spec. A
     * bare path defaults to the {@code minecraft} namespace, matching {@link NamespacedKey#fromString}.
     */
    public static Optional<Enchantment> enchantmentByToken(String rawId) {
        NamespacedKey key = NamespacedKey.fromString(rawId.trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(key));
    }

    /** The {@link Attribute} for {@code spec}'s normalised id, present only when it resolves in the registry. */
    public static Optional<Attribute> attribute(AttributeSpec spec) {
        NamespacedKey key = NamespacedKey.fromString(spec.attributeId());
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ATTRIBUTE)
                    .get(key));
        } catch (RuntimeException registryUnavailable) {
            return Optional.empty();
        }
    }

    /**
     * The {@link EquipmentSlotGroup} for a case-insensitive token, present only when it names one. A blank token is
     * the whole-item {@code any}; the friendly aliases ({@code hand}/{@code mainhand}) map onto the same group the
     * client uses. An unknown token is empty so the caller renders the localized slot failure.
     */
    public static Optional<EquipmentSlotGroup> slotGroup(String token) {
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "", "any" -> Optional.of(EquipmentSlotGroup.ANY);
            case "hand", "mainhand", "main_hand" -> Optional.of(EquipmentSlotGroup.MAINHAND);
            case "off_hand", "offhand" -> Optional.of(EquipmentSlotGroup.OFFHAND);
            case "feet" -> Optional.of(EquipmentSlotGroup.FEET);
            case "legs" -> Optional.of(EquipmentSlotGroup.LEGS);
            case "chest" -> Optional.of(EquipmentSlotGroup.CHEST);
            case "head" -> Optional.of(EquipmentSlotGroup.HEAD);
            case "armor" -> Optional.of(EquipmentSlotGroup.ARMOR);
            case "body" -> Optional.of(EquipmentSlotGroup.BODY);
            default -> Optional.empty();
        };
    }
}
