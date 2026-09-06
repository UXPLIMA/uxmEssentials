package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

/**
 * Thrown when a menu spec cannot be turned into a {@link MenuSpec}. A malformed value, an out-of-range row
 * count, a slot that falls outside the inventory, or an I/O failure reading the file. The message names the
 * offending file and key so an operator can find the typo in their conf without reading a stack trace. Loading
 * a spec is a fail-fast operation: a broken spec is a configuration error the operator must fix, never something
 * the engine silently papers over with a half-built menu.
 */
public final class MenuSpecException extends RuntimeException {

    public MenuSpecException(String message) {
        super(message);
    }

    public MenuSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
