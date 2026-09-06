package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EditorSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.MenuTitles;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The one re-render of an editor a property's reopen hook runs, shared by the {@code Menus} façade and the click
 * listener. It hops to the viewer's entity thread, then repaints in one of two ways depending on what the viewer is
 * looking at: when this holder's editor window is still the top inventory (the common toggle/step case, where the
 * window never closed) it clears the slot routing and repaints the same inventory in place, no second
 * {@code openInventory}, the live window reused so the one listener and one teardown keep owning it. When the editor
 * window is no longer showing, because the property navigated away to a child confirm/sub-menu that has since closed
 *. It re-opens a fresh editor window on the same holder, which is the spec's "back reopens the parent editor"
 * contract: the parent the viewer left is rebuilt, leak-free, under the same holder lineage.
 *
 * <p>Centralising it here keeps the {@code runtime}→{@code menu} edge acyclic: the listener (in {@code runtime}) and
 * the façade (in {@code menu}) both call this rather than the listener reaching into the façade.
 */
@NullMarked
public final class EditorRefresh {

    private EditorRefresh() {}

    /** Hop to the viewer's thread and re-render {@code holder}'s editor: repaint in place, or re-open if it closed. */
    public static void reRender(MenuHolder holder, EditorRenderer renderer, Scheduler scheduler) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(scheduler, "scheduler");
        scheduler.onEntity(holder.ctx().viewer(), () -> repaint(holder, renderer));
    }

    private static void repaint(MenuHolder holder, EditorRenderer renderer) {
        EditorState state = holder.editor().orElse(null);
        if (state == null) {
            return;
        }
        Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
        if (live == null) {
            return;
        }
        EditorSpec spec = (EditorSpec) state.spec();
        if (showsThisEditor(live, holder)) {
            state.clearSlots();
            renderer.populate(
                    holder.getInventory(), spec, state, live, holder.ctx().viewer());
            return;
        }
        reopen(holder, state, spec, live, renderer);
    }

    /** Whether {@code live}'s open top inventory is this holder's editor window (vs a closed/navigated-away window). */
    private static boolean showsThisEditor(Player live, MenuHolder holder) {
        Inventory top = live.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder h && h == holder;
    }

    /** Re-open a fresh editor window on the same holder, the "back to the parent editor" path after a child closed. */
    private static void reopen(
            MenuHolder holder, EditorState state, EditorSpec spec, Player live, EditorRenderer renderer) {
        state.clearSlots();
        Inventory inv = Bukkit.createInventory(
                holder,
                spec.layout().rows() * 9,
                MenuTitles.centre(spec.title(holder.ctx().viewer(), state.subject())));
        holder.attach(inv);
        renderer.populate(inv, spec, state, live, holder.ctx().viewer());
        live.openInventory(inv);
    }
}
