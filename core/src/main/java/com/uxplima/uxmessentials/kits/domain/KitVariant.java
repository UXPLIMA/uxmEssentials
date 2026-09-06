package com.uxplima.uxmessentials.kits.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A per-rank variant of a kit: a permission node that unlocks it plus the stacks it grants, with optional
 * cooldown and cost overrides. A kit may carry several variants ordered best-first; at claim, list, and
 * preview time the {@link KitDefinition} resolves the first variant whose permission the viewer holds and
 * grants that variant's items, falling back to the base kit when the player holds none. This lets one
 * {@code /kit daily} hand out richer loot to higher ranks without an admin defining a separate kit per rank.
 *
 * <p>The permission is resolved through the shared {@code Permissions} port, the same numbered-node
 * convention the cooldown tiers use ({@code uxmessentials.kit.tier.<name>}), so the kernel never inspects
 * the node itself, it only carries it. A variant with no cooldown override inherits the base kit's cooldown;
 * a variant with no cost override inherits the base kit's cost. The items list is non-empty: a variant that
 * granted nothing would be indistinguishable from the player simply lacking it.
 *
 * @param permission the node a viewer must hold for this variant to apply
 * @param items the stacks this variant grants in place of the base kit's, in definition order
 * @param cooldown the cooldown override when present; empty inherits the base kit's cooldown
 * @param cost the cost override when present; empty inherits the base kit's cost
 */
public record KitVariant(String permission, List<KitItem> items, Optional<Duration> cooldown, Optional<KitCost> cost) {

    public KitVariant {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(cost, "cost");
        if (permission.isBlank()) {
            throw new IllegalArgumentException("kit variant permission must not be blank");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("kit variant must grant at least one item: " + permission);
        }
        if (cooldown.isPresent() && cooldown.get().isNegative()) {
            throw new IllegalArgumentException("kit variant cooldown must not be negative: " + cooldown.get());
        }
        items = List.copyOf(items);
    }

    /** A variant with its own items and no cooldown or cost override (it inherits the base kit's). */
    public static KitVariant of(String permission, List<KitItem> items) {
        return new KitVariant(permission, items, Optional.empty(), Optional.empty());
    }
}
