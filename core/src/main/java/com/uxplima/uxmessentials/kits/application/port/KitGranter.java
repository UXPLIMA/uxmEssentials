package com.uxplima.uxmessentials.kits.application.port;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Delivers a kit's items into a player's inventory. The adapter deserializes each {@link KitItem}'s opaque
 * payload back into a Bukkit {@code ItemStack} and adds it, overflowing to the ground only if the inventory
 * is full, so the {@code :core} layer asks for items to be granted without ever touching an item type.
 *
 * <p>The grant is the last step of a claim, run after every gate (permission, cooldown, one-time, cost) has
 * passed, so it does not itself decide whether the claim is allowed. It reports whether everything fit so
 * the use case can surface an inventory-full notice; the items are still delivered (dropped at the player's
 * feet) when they did not all fit, matching the common kit-plugin behaviour.
 */
public interface KitGranter {

    /**
     * Called before the grant is executed to check if any external event cancels it.
     * Returns true if the grant should proceed, false if it should be cancelled.
     */
    default boolean preGrant(PlayerRef recipient, KitDefinition kit) {
        return true;
    }

    /**
     * Whether {@code recipient}'s inventory has room for {@code kit}'s items. Consulted by the claim use case
     * only for a kit whose {@code on-full} policy is {@code DENY}, <em>before</em> any stock is reserved or any
     * cost is charged, so a refusal for want of space leaves no side effects to compensate. The default returns
     * {@code true} (room always available), which keeps the {@code DROP} policy and every test fake unaffected.
     */
    default boolean hasRoomFor(PlayerRef recipient, KitDefinition kit) {
        return true;
    }

    /**
     * Grant kit {@code kit} to {@code recipient}. Returns the outcome so the claim use case can warn when the
     * inventory could not hold everything; a {@code false} {@code fitInInventory} still means the items were
     * delivered (overflow dropped at the player's feet), not that the claim failed. A kit set to
     * {@code on-full: deny} is gated out earlier by {@link #hasRoomFor}, so the grant itself only ever runs
     * once the items are known to fit (or are allowed to overflow).
     */
    Grant grant(PlayerRef recipient, KitDefinition kit);

    /**
     * The result of a grant.
     *
     * @param fitInInventory true when every stack fit in the inventory, false when some overflowed to the
     *     ground
     */
    record Grant(boolean fitInInventory) {

        /** Everything fit in the inventory. */
        public static Grant complete() {
            return new Grant(true);
        }

        /** Some stacks overflowed to the ground. */
        public static Grant overflowed() {
            return new Grant(false);
        }
    }
}
