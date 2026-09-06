package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Fired just before a menu button's click handling runs: its conditions, requirements and actions. Another plugin
 * can cancel it to veto the click: a cancelled event skips the whole click-handling, so none of the button's actions
 * fire. This is the engine's own cancellable hook and is independent of the vanilla {@link
 * org.bukkit.event.inventory.InventoryClickEvent}, which the engine always cancels so a menu item never moves;
 * cancelling this event does not un-cancel that one.
 *
 * <p>The event fires on the viewer's own region thread (the thread the click already runs on), which on Folia is that
 * entity's region rather than a single main thread. A listener must therefore respect Folia threading: it may read
 * the payload and cancel freely, but any world or entity work it triggers must be scheduled onto the owning region,
 * not run inline against another region's state.
 */
@NullMarked
public final class MenuClickEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player viewer;
    private final String menuId;
    private final int slot;

    @Nullable private final String itemId;

    private boolean cancelled;

    public MenuClickEvent(Player viewer, String menuId, int slot, @Nullable String itemId) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.menuId = Objects.requireNonNull(menuId, "menuId");
        this.slot = slot;
        this.itemId = itemId;
    }

    /** The player who clicked the menu. */
    public Player getViewer() {
        return viewer;
    }

    /** The registered spec id of the clicked menu. */
    public String getMenuId() {
        return menuId;
    }

    /** The raw slot index that was clicked. */
    public int getSlot() {
        return slot;
    }

    /**
     * The clicked item spec's own id (the operator's key for it in the menu's {@code items {}} block) when it can be
     * resolved, else {@code null}. It is a best-effort lookup: a list-cell click renders the list's nested template,
     * which carries no top-level key, so such a click reports {@code null}.
     */
    @Nullable public String getItemId() {
        return itemId;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
