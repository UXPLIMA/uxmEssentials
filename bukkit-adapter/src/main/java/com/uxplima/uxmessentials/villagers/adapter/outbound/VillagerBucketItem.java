package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.Pdc;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The one place a live villager is turned into the "captured villager" item and back. Picking a villager up encodes
 * everything the placed-back villager must reproduce, its profession, its biome type, its level, and its full trade
 * set. Into a single {@code VILLAGER_SPAWN_EGG} carrying our marker flag; placing it decodes those back onto a freshly
 * spawned villager. The trade set rides {@link VillagerRecipeCodec} (the same encoder the trade manager uses), so the
 * amounts and metadata of every recipe survive the round trip.
 *
 * <p>The profession and type are stored as their registry-key strings and resolved back through the Bukkit registries;
 * a key that no longer resolves (a removed data-pack profession) is simply skipped, leaving the restored villager with
 * its default, rather than failing the placement. The level is clamped into the vanilla one-to-five band on restore,
 * and the recipes are applied <em>last</em>: after the profession, which would otherwise re-roll a villager's trades.
 *
 * <h2>Concurrency</h2>
 * The item's PDC is read and written off any live entity (it is item state, not entity state); {@link #capture} reads
 * and {@link #restore} writes the live villager, each on the villager's own region thread. Every {@link NamespacedKey}
 * is created once as a constant, never on a hot path.
 */
@NullMarked
public final class VillagerBucketItem {

    /** The item a captured villager becomes: a spawn egg the marker flag re-skins as our own item. */
    private static final Material MATERIAL = Material.VILLAGER_SPAWN_EGG;

    /** The lowest / highest villager level the vanilla merchant accepts; a decoded level is clamped into this band. */
    private static final int MIN_LEVEL = 1;

    private static final int MAX_LEVEL = 5;

    private static final NamespacedKey MARKER = key("villager_bucket");
    private static final NamespacedKey PROFESSION = key("villager_bucket_profession");
    private static final NamespacedKey TYPE = key("villager_bucket_type");
    private static final NamespacedKey LEVEL = key("villager_bucket_level");
    private static final NamespacedKey RECIPES = key("villager_bucket_recipes");

    /** Whether {@code item} is one of our captured-villager items (carries the marker flag). */
    public boolean isBucket(ItemStack item) {
        Objects.requireNonNull(item, "item");
        return item.getType() == MATERIAL && PdcFlag.get(item.getPersistentDataContainer(), MARKER);
    }

    /** Encode {@code villager}'s profession, type, level, and trades into a named captured-villager item. */
    public ItemStack capture(Villager villager, Component name) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(name, "name");
        String profession = villager.getProfession().key().asString();
        String type = villager.getVillagerType().key().asString();
        int level = villager.getVillagerLevel();
        byte[] recipes = VillagerRecipeCodec.encode(villager.getRecipes());
        return ItemBuilder.of(MATERIAL)
                .name(name)
                .editPersistentData(pdc -> {
                    PdcFlag.set(pdc, MARKER, true);
                    Pdc.set(pdc, PROFESSION, PersistentDataType.STRING, profession);
                    Pdc.set(pdc, TYPE, PersistentDataType.STRING, type);
                    Pdc.set(pdc, LEVEL, PersistentDataType.INTEGER, level);
                    Pdc.set(pdc, RECIPES, PersistentDataType.BYTE_ARRAY, recipes);
                })
                .build();
    }

    /** Decode {@code item}'s stored villager onto {@code villager}, freshly spawned for a placement. */
    public void restore(Villager villager, ItemStack item) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(item, "item");
        Pdc.PdcReader pdc = Pdc.read(item);
        // Profession first: setting it re-rolls a villager's trades, so the stored recipes are applied last.
        pdc.get(PROFESSION, PersistentDataType.STRING)
                .flatMap(VillagerBucketItem::profession)
                .ifPresent(villager::setProfession);
        pdc.get(TYPE, PersistentDataType.STRING)
                .flatMap(VillagerBucketItem::type)
                .ifPresent(villager::setVillagerType);
        int level = pdc.getOrDefault(LEVEL, PersistentDataType.INTEGER, MIN_LEVEL);
        villager.setVillagerLevel(Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level)));
        pdc.get(RECIPES, PersistentDataType.BYTE_ARRAY)
                .map(VillagerRecipeCodec::decode)
                .ifPresent(villager::setRecipes);
    }

    private static Optional<Villager.Profession> profession(String storedKey) {
        NamespacedKey key = NamespacedKey.fromString(storedKey);
        return key == null ? Optional.empty() : Optional.ofNullable(Registry.VILLAGER_PROFESSION.get(key));
    }

    private static Optional<Villager.Type> type(String storedKey) {
        NamespacedKey key = NamespacedKey.fromString(storedKey);
        return key == null ? Optional.empty() : Optional.ofNullable(Registry.VILLAGER_TYPE.get(key));
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("uxmessentials:" + value), value + " key");
    }
}
