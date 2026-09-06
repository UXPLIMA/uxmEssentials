package com.uxplima.uxmessentials.vote.domain.reward;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One item to grant as part of a vote reward, described as plain data so the domain stays Bukkit-free:
 * the material name (e.g. {@code DIAMOND}), how many to give, an optional display name, and optional lore
 * lines. The adapter resolves the material and builds the {@code ItemStack}; the catalog and engine only
 * carry the operator's intent. The material and name strings are written as authored: they are not
 * player-facing {@code MessageKey} content.
 *
 * @param material the material name to grant, validated non-blank (adapter resolves it to a type)
 * @param amount how many to grant, at least one
 * @param name an optional display name for the granted stack
 * @param lore the optional lore lines, in order (empty when none)
 */
public record ItemReward(String material, int amount, Optional<String> name, List<String> lore) {

    public ItemReward {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lore, "lore");
        if (material.isBlank()) {
            throw new IllegalArgumentException("material must not be blank");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least one: " + amount);
        }
        lore = List.copyOf(lore);
    }
}
