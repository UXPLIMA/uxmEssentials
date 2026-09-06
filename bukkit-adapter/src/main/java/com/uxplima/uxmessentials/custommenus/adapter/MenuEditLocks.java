package com.uxplima.uxmessentials.custommenus.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * The one-editor-per-menu lock the in-game menu editor takes so two operators can never edit, and then save over,
 * the same menu at once. When a viewer opens a menu's slot grid or property editor the editor calls
 * {@link #tryAcquire}: it succeeds (empty result) for the first viewer, and for that same viewer again (a lock is
 * reentrant, so navigating between the grid, item and property editors of the menu they hold never locks them out),
 * but a different viewer opening the held menu is refused and handed the holder's name to show. This realises the
 * "never rewrite a menu another viewer has open" invariant the spec service leans on.
 *
 * <p>Bukkit-free (it keys everything on a plain {@link UUID} and display name) so it is shared between the editor
 * views and the quit listener and exercised by plain JUnit. It stays bounded and self-cleaning: a viewer holds at
 * most one menu at a time (acquiring a second releases the first), {@link #release} drops a viewer's lock when they
 * return to the menu browser, and the quit listener releases on disconnect, so the two maps only ever hold entries
 * for the operators currently editing. Every method is {@code synchronized}, since the views (on entity threads) and
 * the quit listener touch the same registry.
 */
@NullMarked
public final class MenuEditLocks {

    /** The menu a viewer currently holds, keyed by menu id: the read a second viewer's refusal consults. */
    private final Map<String, Holder> byMenu = new HashMap<>();

    /** The reverse index, so a release (or a switch to another menu) can find and drop a viewer's lock in one step. */
    private final Map<UUID, String> byViewer = new HashMap<>();

    /**
     * Try to take the edit lock on {@code menuId} for {@code viewer}. Returns {@link Optional#empty()} when the lock is
     * now held by {@code viewer} (because it was free, or because {@code viewer} already held it (reentrant)) and the
     * present holder's display name when another viewer holds it, so the caller can refuse with a "being edited by …"
     * line. Acquiring a new menu releases any other menu the same viewer held, so a viewer never pins two menus.
     */
    public synchronized Optional<String> tryAcquire(String menuId, UUID viewer, String viewerName) {
        Objects.requireNonNull(menuId, "menuId");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerName, "viewerName");
        Holder held = byMenu.get(menuId);
        if (held != null && !held.uuid().equals(viewer)) {
            return Optional.of(held.name());
        }
        String previous = byViewer.put(viewer, menuId);
        if (previous != null && !previous.equals(menuId)) {
            releaseMenuIfOwnedBy(previous, viewer);
        }
        byMenu.put(menuId, new Holder(viewer, viewerName));
        return Optional.empty();
    }

    /** Release whatever menu {@code viewer} holds; a no-op when they hold none, the return-to-browser and quit path. */
    public synchronized void release(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        String menuId = byViewer.remove(viewer);
        if (menuId != null) {
            releaseMenuIfOwnedBy(menuId, viewer);
        }
    }

    /** The display name of the viewer holding {@code menuId}, or empty when the menu is free, a test/read helper. */
    public synchronized Optional<String> heldBy(String menuId) {
        Objects.requireNonNull(menuId, "menuId");
        Holder held = byMenu.get(menuId);
        return held == null ? Optional.empty() : Optional.of(held.name());
    }

    /** Whether {@code viewer} currently holds the lock on {@code menuId}, a test/read helper. */
    public synchronized boolean holds(String menuId, UUID viewer) {
        Objects.requireNonNull(menuId, "menuId");
        Objects.requireNonNull(viewer, "viewer");
        Holder held = byMenu.get(menuId);
        return held != null && held.uuid().equals(viewer);
    }

    /** Drop the {@code menuId} entry only when it is still {@code viewer}'s, so a release never steals another's lock. */
    private void releaseMenuIfOwnedBy(String menuId, UUID viewer) {
        Holder held = byMenu.get(menuId);
        if (held != null && held.uuid().equals(viewer)) {
            byMenu.remove(menuId);
        }
    }

    /** One viewer's claim on a menu: their identity and the display name a refusal shows. */
    private record Holder(UUID uuid, String name) {
        private Holder {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(name, "name");
        }
    }
}
