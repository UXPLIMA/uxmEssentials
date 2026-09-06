package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListControlSyntax.SortDirection;

/**
 * The handle the engine hands a menu action so a click can drive the very window it fired in, repaint the whole
 * menu, repaint one slot, or reset it to the first page: rather than only touching the world outside it. The
 * {@link MenuListener} binds a real one to the clicked {@link MenuHolder} and threads it onto the
 * {@link MenuActionContext}; every operation runs through the same viewer's-entity-thread hop and the same repaint
 * path a pagination click uses, so a control action never touches the inventory off the viewer's region thread and
 * never opens a second window.
 *
 * <p>A context built outside a live click (a unit test, or a feature invoking a binding directly) carries
 * {@link #NOOP} instead, so a {@code refresh}/{@code reset} there is a harmless no-op rather than a null
 * dereference. That is what keeps the four-argument {@link MenuActionContext} constructor (the one every existing
 * call-site uses) safe without change.
 *
 * <p>Engine-internal: it lives in {@code runtime/} and is only ever produced by the listener and consumed by the
 * sibling {@code vocab/} action pack, both inside the engine.
 */
public interface MenuControl {

    /** A control bound to no window: every operation does nothing. Carried by a context built outside a live click. */
    MenuControl NOOP = new MenuControl() {
        @Override
        public void refresh() {}

        @Override
        public void refreshSlot(int slot) {}

        @Override
        public void resetPagination() {}

        @Override
        public void sortList(String listId, SortDirection direction) {}

        @Override
        public void filterList(String listId, String key, String value) {}

        @Override
        public void searchList(String listId, String key) {}
    };

    /** Repaint the whole menu at its current page, rebuilding its click routing from the spec. */
    void refresh();

    /** Repaint the menu so a single slot redraws; an out-of-range slot is a no-op. */
    void refreshSlot(int slot);

    /** Reset the menu to page zero and repaint it there. */
    void resetPagination();

    /**
     * Move the paged list named by {@code listId} to the next / previous / first offered sort, then re-query its source
     * for page zero and repaint. A list id this menu does not carry is a logged no-op, not a crash; a list that offers
     * no sorts leaves the order unchanged and simply re-queries.
     */
    void sortList(String listId, SortDirection direction);

    /**
     * Set (or, for an empty {@code value}, clear) the filter {@code key} on the paged list named by {@code listId},
     * then re-query its source for page zero and repaint. A list id this menu does not carry is a logged no-op.
     */
    void filterList(String listId, String key, String value);

    /**
     * Prompt the viewer for a line of text and, on submit, store it as the filter {@code key} on the paged list named
     * by {@code listId}, then re-query its source for page zero and repaint. A cancel changes nothing. A list id this
     * menu does not carry, or an engine wired without a text prompt, is a logged no-op.
     */
    void searchList(String listId, String key);
}
