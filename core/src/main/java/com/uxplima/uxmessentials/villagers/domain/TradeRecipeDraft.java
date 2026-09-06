package com.uxplima.uxmessentials.villagers.domain;

/**
 * The pure shape of one merchant trade as the trade manager is about to build it: how many buy ingredients it carries
 * and whether it has a sell result. A villager recipe is only well-formed when it costs at least one ingredient and
 * yields a result, and a merchant recipe accepts at most two ingredients. This value object owns that rule so the
 * adapter, which reads the live GUI slots and holds the Bukkit {@code MerchantRecipe}, never re-decides validity
 * inline. The adapter maps each editing row to a draft and only materialises a recipe for a {@link #isValid() valid}
 * one; an incomplete row (a result with no cost, or a cost with no result) is simply dropped.
 *
 * @param ingredientCount the number of non-empty buy ingredients the row carries (never negative)
 * @param hasResult whether the row carries a sell result
 */
public record TradeRecipeDraft(int ingredientCount, boolean hasResult) {

    /** A merchant recipe accepts at most two buy ingredients (the vanilla trade shape). */
    public static final int MAX_INGREDIENTS = 2;

    public TradeRecipeDraft {
        if (ingredientCount < 0) {
            throw new IllegalArgumentException("ingredientCount must not be negative: " + ingredientCount);
        }
    }

    /**
     * Whether this draft forms a usable trade: it must carry a result and between one and {@link #MAX_INGREDIENTS}
     * buy ingredients. A row with no result, no cost, or more ingredients than a merchant recipe can hold is not a
     * trade and the manager leaves it out of the villager's recipe set.
     */
    public boolean isValid() {
        return hasResult && ingredientCount >= 1 && ingredientCount <= MAX_INGREDIENTS;
    }
}
