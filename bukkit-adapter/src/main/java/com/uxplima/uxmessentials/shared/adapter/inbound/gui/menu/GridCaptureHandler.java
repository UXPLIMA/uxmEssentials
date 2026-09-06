package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The callback the engine routes a <em>capture</em> to on a capture-enabled grid: the operator drags or places one of
 * their own items onto a content cell, or shift-clicks one out of their inventory onto the canvas. Unlike
 * {@link GridClickHandler}, which fires for an editor gesture (pick up, move, clear, open the sub-editor), this fires
 * only when a real item's definition is being stamped into a slot. The engine has already cancelled the underlying
 * event, so the operator's real item never moves. {@code copy} is a defensive clone of it, and the consumer's job is
 * to record its definition into the edited model (typically a serialized token) and repaint. Because nothing is ever
 * transferred into or out of the top inventory, no duplicate can be produced.
 *
 * <p>Handed the {@link GridView} for the open window (so the consumer can repaint after mutating its model), the live
 * {@link Player} the engine re-resolved on the viewer's entity thread, the <em>menu</em> slot the capture maps to
 * (already un-paged by the engine), and a clone of the captured item. A grid opened with a non-null capture handler on
 * its {@link GridHandlers} is "capture-enabled"; one opened without stays blanket-cancelled like every other menu.
 */
@FunctionalInterface
public interface GridCaptureHandler {

    /** Stamp {@code copy}'s definition into the cell that maps to {@code menuSlot}; the real item was never moved. */
    void capture(GridView view, Player viewer, int menuSlot, ItemStack copy);
}
