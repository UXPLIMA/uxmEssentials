package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption resolution of an item or block hologram's stored content, a {@code Material} name and a
 * BlockData string, both pure strings in the domain: into the live Bukkit types the lib needs. Both are
 * fail-soft: an unknown material name or an unparseable BlockData string yields {@code null} rather than
 * throwing, so the renderer can log and skip one bad hologram without crashing the whole render pass.
 */
@NullMarked
final class HologramModels {

    private HologramModels() {}

    /**
     * Resolve a stored item-material name into a single-stack {@link ItemStack}, or {@code null} for a blank or
     * unknown name. A {@code Material} name is the v1 item content; storing a full serialized custom item (via
     * the shared item codec) is a deliberate future enhancement, so a v1 item hologram shows the vanilla item.
     */
    static @Nullable ItemStack itemOf(@Nullable String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(materialName.strip().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            return null;
        }
        return new ItemStack(material);
    }

    /**
     * Parse a stored BlockData string (e.g. {@code minecraft:oak_log[axis=y]}) into live {@link BlockData}, or
     * {@code null} for a blank or invalid string. {@code Bukkit.createBlockData} throws
     * {@link IllegalArgumentException} on a malformed string; that is caught and turned into the fail-soft null.
     */
    static @Nullable BlockData blockOf(@Nullable String blockData) {
        if (blockData == null || blockData.isBlank()) {
            return null;
        }
        try {
            return Bukkit.createBlockData(blockData.strip());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /**
     * Build a {@code PLAYER_HEAD} {@link ItemStack} wearing the skin carried by the base64 textures-property
     * value, or {@code null} for a blank texture. The profile uuid is derived deterministically from the texture
     * so a re-render reuses the same profile (no client-side skin-cache churn on reload). The texture carries the
     * skin itself, so a skull item needs no signature to render it.
     */
    static @Nullable ItemStack headOf(@Nullable String texture) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta skull)) {
            return null;
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", texture));
        skull.setPlayerProfile(profile);
        head.setItemMeta(skull);
        return head;
    }

    /**
     * Resolve a stored entity-type name into a spawnable, living {@link org.bukkit.entity.EntityType}, or
     * {@code null} for a blank, unknown, non-spawnable, or non-living name. Only a living entity can render as a
     * frozen decorative hologram (the renderer disables its AI/gravity), so a non-living or unspawnable type is
     * failed soft rather than spawned.
     */
    static org.bukkit.entity.@Nullable EntityType entityTypeOf(@Nullable String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return null;
        }
        org.bukkit.entity.EntityType type;
        try {
            type = org.bukkit.entity.EntityType.valueOf(entityType.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        Class<?> entityClass = type.getEntityClass();
        boolean living = entityClass != null && org.bukkit.entity.LivingEntity.class.isAssignableFrom(entityClass);
        return type.isSpawnable() && living ? type : null;
    }
}
