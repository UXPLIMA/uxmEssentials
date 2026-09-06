package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;

/**
 * Collapses a menu's overlapping items into the single item that should render in each slot. Several items can
 * claim the same slot (a high-priority decoration over a filler, say) so the winner is the visible item with
 * the greatest {@code priority}. Items are walked in their declared order, which both keeps ties stable (the
 * first author to claim a priority keeps the slot) and lets a later item only displace an earlier one when it
 * strictly outranks it. An item whose view conditions fail is dropped entirely, freeing its slots for whatever
 * sits beneath it.
 */
public final class PriorityLayering {

    private PriorityLayering() {}

    /**
     * Resolves each targeted slot to the highest-priority visible item. {@code viewPasses} decides visibility;
     * items it rejects are skipped for all their slots. The returned map keeps slots in first-claimed order.
     */
    public static Map<Integer, MenuItemSpec> resolve(
            Collection<MenuItemSpec> items, Predicate<MenuItemSpec> viewPasses) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(viewPasses, "viewPasses");
        Map<Integer, MenuItemSpec> resolved = new LinkedHashMap<>();
        for (MenuItemSpec item : items) {
            if (viewPasses.test(item)) {
                layer(resolved, item);
            }
        }
        return resolved;
    }

    private static void layer(Map<Integer, MenuItemSpec> resolved, MenuItemSpec item) {
        for (int slot : item.slots().slots()) {
            MenuItemSpec incumbent = resolved.get(slot);
            if (incumbent == null || item.priority() > incumbent.priority()) {
                resolved.put(slot, item);
            }
        }
    }
}
