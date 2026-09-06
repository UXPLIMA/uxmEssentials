package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/**
 * Fired just before a menu opens for a viewer, at the single choke-point every open funnels through, a fresh open,
 * a {@code back} step, or a reopen-last. Another plugin can cancel it to veto the open: a cancelled event shows the
 * viewer neither a chest nor a native Bedrock form. An uncancelled event is the default, so a menu with no listener
 * opens exactly as before.
 *
 * <p>The event fires on the viewer's own region thread (the thread the engine opens the window on), which on Folia is
 * that entity's region rather than a single main thread. A listener must therefore respect Folia threading: it may
 * read the payload and cancel freely, but any world or entity work it triggers must be scheduled onto the owning
 * region, not run inline against another region's state.
 */
@NullMarked
public final class MenuOpenEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player viewer;
    private final String menuId;
    private final int page;
    private boolean cancelled;

    public MenuOpenEvent(Player viewer, String menuId, int page) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.menuId = Objects.requireNonNull(menuId, "menuId");
        this.page = page;
    }

    /** The player the menu is about to open for. */
    public Player getViewer() {
        return viewer;
    }

    /** The registered spec id of the menu that is about to open. */
    public String getMenuId() {
        return menuId;
    }

    /** The zero-based page the menu is about to open on. */
    public int getPage() {
        return page;
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
