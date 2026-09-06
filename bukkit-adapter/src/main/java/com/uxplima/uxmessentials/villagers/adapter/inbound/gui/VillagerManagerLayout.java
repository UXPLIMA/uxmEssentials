package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.villagers.domain.TradeRecipeDraft;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The pure translation between the trade-manager window's item region and a villager's {@link MerchantRecipe} set.
 * The region is a flat run of slots in triples (buy-A, buy-B, sell) one triple per editable trade; where those
 * slots sit in the window is the spec file's business ({@link VillagerManagerWindow}), and only their order matters
 * here. Editing a trade means dragging its buy/sell stacks (their amounts are the trade amounts) into a triple;
 * filling an empty triple adds a trade; clearing one drops that trade.
 *
 * <p>{@link #readRecipes} maps the region back to a recipe set on close: each triple that forms a
 * {@link TradeRecipeDraft#isValid() valid} draft becomes a recipe, borrowing the use-limit and reward metadata of the
 * recipe that occupied that position when the window opened, and any trade the villager had beyond the editable
 * triples is carried through untouched so a librarian's deeper trade list is never truncated by the editor.
 */
@NullMarked
final class VillagerManagerLayout {

    /** The use limit a manager-built recipe carries when its position had no prior recipe to borrow from. */
    private static final int DEFAULT_MAX_USES = 999;

    private VillagerManagerLayout() {}

    /** The region-ordered stacks of {@code recipes}: for each of {@code trades} triples, buy-A, buy-B, then sell. */
    static List<@Nullable ItemStack> paint(List<MerchantRecipe> recipes, int trades) {
        Objects.requireNonNull(recipes, "recipes");
        List<@Nullable ItemStack> painted = new ArrayList<>(trades * VillagerManagerWindow.SLOTS_PER_TRADE);
        for (int trade = 0; trade < trades; trade++) {
            MerchantRecipe recipe = trade < recipes.size() ? recipes.get(trade) : null;
            List<ItemStack> ingredients = recipe == null ? List.of() : recipe.getIngredients();
            painted.add(copyOf(ingredients.isEmpty() ? null : ingredients.get(0)));
            painted.add(copyOf(ingredients.size() > 1 ? ingredients.get(1) : null));
            painted.add(copyOf(recipe == null ? null : recipe.getResult()));
        }
        return painted;
    }

    /**
     * Read {@code region} back into a recipe set: each valid triple becomes a recipe (borrowing that position's
     * original use-limit / reward metadata where one existed), followed by any of {@code originals} beyond the
     * editable triples.
     */
    static List<MerchantRecipe> readRecipes(List<@Nullable ItemStack> region, List<MerchantRecipe> originals) {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(originals, "originals");
        int trades = region.size() / VillagerManagerWindow.SLOTS_PER_TRADE;
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (int trade = 0; trade < trades; trade++) {
            readTrade(region, trade, originals).ifPresent(recipes::add);
        }
        for (int index = trades; index < originals.size(); index++) {
            recipes.add(originals.get(index));
        }
        return recipes;
    }

    private static Optional<MerchantRecipe> readTrade(
            List<@Nullable ItemStack> region, int trade, List<MerchantRecipe> originals) {
        int first = trade * VillagerManagerWindow.SLOTS_PER_TRADE;
        List<ItemStack> ingredients = ingredientsOf(region.get(first), region.get(first + 1));
        ItemStack sell = region.get(first + 2);
        TradeRecipeDraft draft = new TradeRecipeDraft(ingredients.size(), isPresent(sell));
        if (!draft.isValid() || sell == null) {
            return Optional.empty();
        }
        return Optional.of(buildRecipe(sell, ingredients, template(originals, trade)));
    }

    private static MerchantRecipe buildRecipe(
            ItemStack sell, List<ItemStack> ingredients, @Nullable MerchantRecipe template) {
        int maxUses = template != null ? template.getMaxUses() : DEFAULT_MAX_USES;
        boolean experience = template != null && template.hasExperienceReward();
        MerchantRecipe recipe = new MerchantRecipe(sell.clone(), 0, maxUses, experience);
        if (template != null) {
            recipe.setVillagerExperience(template.getVillagerExperience());
            recipe.setPriceMultiplier(template.getPriceMultiplier());
        }
        List<ItemStack> clones = new ArrayList<>(ingredients.size());
        for (ItemStack ingredient : ingredients) {
            clones.add(ingredient.clone());
        }
        recipe.setIngredients(clones);
        return recipe;
    }

    private static @Nullable MerchantRecipe template(List<MerchantRecipe> originals, int trade) {
        return trade < originals.size() ? originals.get(trade) : null;
    }

    private static List<ItemStack> ingredientsOf(@Nullable ItemStack buyA, @Nullable ItemStack buyB) {
        List<ItemStack> ingredients = new ArrayList<>(TradeRecipeDraft.MAX_INGREDIENTS);
        if (isPresent(buyA)) {
            ingredients.add(buyA);
        }
        if (isPresent(buyB)) {
            ingredients.add(buyB);
        }
        return ingredients;
    }

    private static boolean isPresent(@Nullable ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private static @Nullable ItemStack copyOf(@Nullable ItemStack stack) {
        return isPresent(stack) ? Objects.requireNonNull(stack).clone() : null;
    }
}
