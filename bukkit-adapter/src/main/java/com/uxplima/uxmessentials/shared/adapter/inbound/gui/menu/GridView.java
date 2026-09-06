package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

/**
 * The handle the engine hands a grid's click handler so it can repaint the open canvas after mutating whatever model
 * it draws from. A grid stays open across clicks (unlike a confirm or selector, which close on the first choice), so
 * a place / move / clear that changes the backing edit session needs the window redrawn in place rather than reopened.
 * {@link #reRender} does exactly that. It re-reads the grid's content supplier and repaints the same inventory at the
 * current page on the viewer's entity thread, so the consumer never touches the holder or a renderer itself.
 */
public interface GridView {

    /** Repaint the open grid in place from its content supplier at the current page; a no-op if the window has closed. */
    void reRender();
}
