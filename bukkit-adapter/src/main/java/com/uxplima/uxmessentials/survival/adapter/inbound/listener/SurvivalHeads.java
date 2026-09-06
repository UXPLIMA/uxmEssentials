package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-side head items head-drop produces: a player's own head (a {@code PLAYER_HEAD} carrying the victim's
 * profile) and the vanilla mob heads. Only the mobs that ship a real head item in vanilla are mapped, the zombie,
 * skeleton, wither skeleton, creeper, piglin, and ender dragon, so head-drop drops an authentic Minecraft head rather
 * than shipping a table of custom textures. A mob without a vanilla head simply drops nothing.
 */
@NullMarked
final class SurvivalHeads {

    private SurvivalHeads() {}

    /** The mobs with a real vanilla head item, and the material each drops. */
    private static final Map<EntityType, Material> MOB_HEADS = mobHeads();

    /** A {@code PLAYER_HEAD} owned by {@code victim}, so it renders and stacks as that player's head. */
    static ItemStack playerHead(Player victim) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(victim);
            head.setItemMeta(meta);
        }
        return head;
    }

    /** The vanilla head material for {@code type}, if that mob has one. */
    static Optional<Material> mobHeadMaterial(EntityType type) {
        return Optional.ofNullable(MOB_HEADS.get(type));
    }

    /** One head item of {@code headMaterial}. */
    static ItemStack mobHead(Material headMaterial) {
        return new ItemStack(headMaterial);
    }

    private static Map<EntityType, Material> mobHeads() {
        Map<EntityType, Material> heads = new EnumMap<>(EntityType.class);
        heads.put(EntityType.ZOMBIE, Material.ZOMBIE_HEAD);
        heads.put(EntityType.SKELETON, Material.SKELETON_SKULL);
        heads.put(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL);
        heads.put(EntityType.CREEPER, Material.CREEPER_HEAD);
        heads.put(EntityType.PIGLIN, Material.PIGLIN_HEAD);
        heads.put(EntityType.ENDER_DRAGON, Material.DRAGON_HEAD);
        return heads;
    }
}
