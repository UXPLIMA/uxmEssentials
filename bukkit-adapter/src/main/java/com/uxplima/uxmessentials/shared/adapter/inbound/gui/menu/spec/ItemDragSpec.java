package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;

/**
 * An item-drag binding on a menu item: a slot that accepts an item the viewer is holding on their cursor. When the
 * viewer clicks the slot while holding a matching item, the engine keeps the click cancelled (vanilla never moves
 * the item, so there is no dupe), validates the cursor against {@link #rules}, and on a pass fires {@link #actions}
 * with the dropped item exposed as {@code %drag_material%}/{@code %drag_amount%}/{@code %drag_name%} placeholders.
 * When {@link #consume} is true the matched amount is then removed from the cursor. A cursor that fails the rules is
 * left untouched, a silent deny, since a "wrong item" message would spam a mis-drop.
 *
 * <p>Bukkit-free by design, like every {@code spec/} type: the {@code ItemStack} matching and the cursor mutation
 * live in the runtime listener, which is the only place allowed to touch Bukkit.
 */
public record ItemDragSpec(ItemRuleSpec rules, boolean consume, List<Ref> actions) {

    public ItemDragSpec {
        Objects.requireNonNull(rules, "rules");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }
}
