package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;

/**
 * The callback the engine routes a grid content-slot click to. It is handed the {@link GridView} for the open window
 * (so the consumer can repaint after a mutation), the live {@link Player} the engine already re-resolved on the
 * viewer's entity thread (so the consumer can message them or open a child window), the <em>menu</em> slot the click
 * resolved to (the absolute slot in the edited menu, already un-paged by the engine's
 * {@code menuSlot = canvasSlot + page*pageSize} math), whether that slot currently holds an item, and the click
 * gesture. The engine makes no editing decision of its own. Placing, moving, clearing and opening a sub-editor are
 * all the consumer's policy: it only translates a raw canvas click into this un-paged, filled-or-empty call.
 */
@FunctionalInterface
public interface GridClickHandler {

    /** Handle a click on the content cell that maps to {@code menuSlot}; {@code filled} says whether it holds an item. */
    void onClick(GridView view, Player player, int menuSlot, boolean filled, ClickKind kind);
}
