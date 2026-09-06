package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

/**
 * The role an item plays in a paginated menu. {@link #NONE} is an ordinary item; the other kinds drive the
 * engine's pagination controls: next page, previous page, or a jump to a specific page.
 */
public enum ItemType {
    NONE,
    NEXT,
    PREVIOUS,
    JUMP
}
