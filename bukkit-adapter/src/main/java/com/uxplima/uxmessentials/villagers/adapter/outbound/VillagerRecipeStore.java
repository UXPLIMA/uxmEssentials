package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import com.uxplima.uxmlib.item.Pdc;
import org.jspecify.annotations.NullMarked;

/**
 * The per-villager custom-trade store the trade manager writes through: when staff edit a villager's trades, the
 * edited recipe set is applied to the live {@link Villager} <em>and</em> serialised into the villager's PDC here, so
 * it outlives the runtime recipes vanilla can re-roll. When a managed villager loads back in, the reapply listener
 * calls {@link #apply(Villager)} to restore the stored set over whatever vanilla handed it.
 *
 * <p>The recipe blob rides {@link PersistentDataType#BYTE_ARRAY} under a single pre-created {@link NamespacedKey}; the
 * serialization itself lives in {@link VillagerRecipeCodec}. A villager that was never managed carries no key, so
 * {@link #hasCustomRecipes} reads {@code false} and {@link #apply} is a no-op for it. An ordinary villager keeps its
 * vanilla trades untouched.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>per-holder PDC</b>. Every read and write goes through a live {@link Villager} on its own region
 * thread (the manager's editor thread, or the entity-add event thread). The {@link NamespacedKey} is created once as
 * a constant, never on a hot path.
 */
@NullMarked
public final class VillagerRecipeStore {

    private static final NamespacedKey RECIPES = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:villagers_recipes"), "villagers_recipes key");

    /** Whether {@code villager} carries a manager-edited custom trade set in its PDC. */
    public boolean hasCustomRecipes(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        return Pdc.has(villager.getPersistentDataContainer(), RECIPES, PersistentDataType.BYTE_ARRAY);
    }

    /** The manager-edited custom trade set stored on {@code villager}, or empty when it was never managed. */
    public Optional<List<MerchantRecipe>> load(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        return Pdc.get(villager.getPersistentDataContainer(), RECIPES, PersistentDataType.BYTE_ARRAY)
                .map(VillagerRecipeCodec::decode);
    }

    /** Serialise {@code recipes} onto {@code villager} as its authoritative custom trade set. */
    public void store(Villager villager, List<MerchantRecipe> recipes) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(recipes, "recipes");
        Pdc.set(
                villager.getPersistentDataContainer(),
                RECIPES,
                PersistentDataType.BYTE_ARRAY,
                VillagerRecipeCodec.encode(recipes));
    }

    /** Reapply {@code villager}'s stored custom trade set to the live merchant; a no-op when it has none. */
    public void apply(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        load(villager).ifPresent(villager::setRecipes);
    }
}
