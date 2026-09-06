package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of the menu a viewer currently has open, as the outbound placeholder source reads it:
 * the spec id, the current page (1-based, the way a player counts pages), the row count, and the typed command
 * arguments the menu was opened with. {@link Menus#currentMenu} builds it from the live window's holder, so it
 * carries no Bukkit or engine-internal type. The outbound {@code papi} adapter can hold it without reaching
 * across the engine's internals fence into the runtime.
 */
public record OpenMenuInfo(String specId, int page, int rows, Map<String, String> arguments) {

    public OpenMenuInfo {
        Objects.requireNonNull(specId, "specId");
        arguments = Map.copyOf(arguments);
    }
}
