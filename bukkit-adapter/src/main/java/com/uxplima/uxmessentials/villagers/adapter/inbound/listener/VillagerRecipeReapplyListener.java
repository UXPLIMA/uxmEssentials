package com.uxplima.uxmessentials.villagers.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeStore;
import org.jspecify.annotations.NullMarked;

/**
 * Restores a manager-edited villager's custom trades whenever it enters a world, on chunk load, on server start, or on
 * a teleport in. The trade manager writes the edited recipe set both to the live merchant and into the villager's PDC;
 * this listener reads that stored set back over whatever vanilla handed the villager, so a trade edit is authoritative
 * against vanilla re-rolls and outlives a reload. A villager that was never managed carries no stored set, so
 * {@link VillagerRecipeStore#apply} is a cheap no-op for the overwhelming majority of villagers.
 *
 * <h2>Folia</h2>
 * {@link EntityAddToWorldEvent} is dispatched on the region that now owns the entity, and the handler only reads that
 * one villager's PDC and re-sets its own recipes on that same thread, a per-entity, region-local touch with no
 * whole-world enumeration.
 */
@NullMarked
public final class VillagerRecipeReapplyListener implements Listener {

    private final VillagerRecipeStore recipeStore;

    public VillagerRecipeReapplyListener(VillagerRecipeStore recipeStore) {
        this.recipeStore = Objects.requireNonNull(recipeStore, "recipeStore");
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Villager villager && recipeStore.hasCustomRecipes(villager)) {
            recipeStore.apply(villager);
        }
    }
}
