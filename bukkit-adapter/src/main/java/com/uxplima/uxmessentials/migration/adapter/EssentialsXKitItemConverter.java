package com.uxplima.uxmessentials.migration.adapter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.google.common.base.Splitter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Turns one EssentialsX kit item descriptor line ({@code "diamond_sword 1 damage_all:5 durability:3"},
 * {@code "wool 1"}) into a Bukkit {@link ItemStack}, the bukkit-side conversion the kit import defers here
 * (docs/12-migration §5.1). The descriptor grammar handled is the realistic subset for a modern server: the
 * material (a name, optionally a {@code name:data} pair), the amount, an optional durability/damage value, and
 * enchantment {@code name:level} tokens. Including the legacy Bukkit enchant names EssentialsX historically
 * wrote ({@code damage_all}, {@code durability}, {@code dig_speed}, …). Item display name, lore, and
 * potion/firework meta are out of scope and ignored, as are pre-1.13 numeric item ids (removed game-side in
 * 1.13 and unresolvable through the 1.21 API). An unresolvable material yields {@link Optional#empty()} so the
 * writer counts the item as skipped rather than import a broken stack; an unresolvable individual enchant is
 * dropped while the stack still imports.
 */
@NullMarked
final class EssentialsXKitItemConverter {

    private static final Splitter WHITESPACE = Splitter.on(' ').trimResults().omitEmptyStrings();

    /** Legacy Bukkit enchantment names EssentialsX wrote, mapped to their modern {@code minecraft:} key paths. */
    private static final Map<String, String> LEGACY_ENCHANTS = Map.ofEntries(
            Map.entry("protection_environmental", "protection"),
            Map.entry("protection_fire", "fire_protection"),
            Map.entry("protection_explosions", "blast_protection"),
            Map.entry("protection_projectile", "projectile_protection"),
            Map.entry("oxygen", "respiration"),
            Map.entry("water_worker", "aqua_affinity"),
            Map.entry("damage_all", "sharpness"),
            Map.entry("damage_undead", "smite"),
            Map.entry("damage_arthropods", "bane_of_arthropods"),
            Map.entry("loot_bonus_mobs", "looting"),
            Map.entry("dig_speed", "efficiency"),
            Map.entry("durability", "unbreaking"),
            Map.entry("loot_bonus_blocks", "fortune"),
            Map.entry("arrow_damage", "power"),
            Map.entry("arrow_knockback", "punch"),
            Map.entry("arrow_fire", "flame"),
            Map.entry("arrow_infinite", "infinity"),
            Map.entry("luck", "luck_of_the_sea"),
            Map.entry("sweeping_edge", "sweeping"));

    private EssentialsXKitItemConverter() {}

    /** Parse {@code descriptor} into a stack, or empty when its material does not resolve on this server. */
    static Optional<ItemStack> toStack(String descriptor) {
        List<String> tokens = WHITESPACE.splitToList(descriptor.strip());
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        ItemSpec spec = parseItemSpec(tokens.get(0));
        Optional<Material> material = resolveMaterial(spec);
        if (material.isEmpty()) {
            return Optional.empty();
        }
        int amount = parseAmount(tokens);
        ItemStack stack = new ItemStack(material.get(), amount);
        applyDamage(stack, spec.data());
        applyEnchants(stack, tokens);
        return Optional.of(stack);
    }

    private static ItemSpec parseItemSpec(String token) {
        int colon = token.indexOf(':');
        if (colon < 0) {
            return new ItemSpec(token, -1);
        }
        String name = token.substring(0, colon);
        int data = parseInt(token.substring(colon + 1)).orElse(-1);
        return new ItemSpec(name, data);
    }

    private static Optional<Material> resolveMaterial(ItemSpec spec) {
        NamespacedKey key = NamespacedKey.fromString(spec.name().toLowerCase(Locale.ROOT));
        if (key == null) {
            return Optional.empty();
        }
        Material material = Registry.MATERIAL.get(key);
        return material != null && material.isItem() ? Optional.of(material) : Optional.empty();
    }

    private static int parseAmount(List<String> tokens) {
        if (tokens.size() < 2) {
            return 1;
        }
        return parseInt(tokens.get(1)).map(amount -> Math.max(1, amount)).orElse(1);
    }

    private static void applyDamage(ItemStack stack, int data) {
        if (data <= 0) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(data);
            stack.setItemMeta(meta);
        }
    }

    private static void applyEnchants(ItemStack stack, List<String> tokens) {
        for (int i = 2; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int colon = token.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            Optional<Integer> level = parseInt(token.substring(colon + 1));
            Optional<Enchantment> enchant = resolveEnchant(token.substring(0, colon));
            if (level.isPresent() && enchant.isPresent()) {
                stack.addUnsafeEnchantment(enchant.get(), Math.max(1, level.get()));
            }
        }
    }

    private static Optional<Enchantment> resolveEnchant(String rawName) {
        String name = rawName.toLowerCase(Locale.ROOT);
        Enchantment direct = lookupEnchant(name);
        if (direct != null) {
            return Optional.of(direct);
        }
        String modern = LEGACY_ENCHANTS.get(name);
        return modern == null ? Optional.empty() : Optional.ofNullable(lookupEnchant(modern));
    }

    private static @Nullable Enchantment lookupEnchant(String path) {
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(path));
    }

    private static Optional<Integer> parseInt(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw.strip()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** The first descriptor token, split into its material name and an optional trailing {@code :data} value. */
    private record ItemSpec(String name, int data) {}
}
