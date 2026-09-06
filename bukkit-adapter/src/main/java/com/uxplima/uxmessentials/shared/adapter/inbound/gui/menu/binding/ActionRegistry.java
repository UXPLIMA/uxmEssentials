package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;

/**
 * Holds the action handlers a spec can fire by id (on open, on close, or on a click). A duplicate id is a wiring
 * mistake, two features both claiming {@code "close"}, so registration fails loudly rather than letting the
 * second silently win.
 */
public final class ActionRegistry {

    private final ConcurrentHashMap<String, Consumer<MenuActionContext>> handlers = new ConcurrentHashMap<>();

    public void register(String id, Consumer<MenuActionContext> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("action already registered: " + id);
        }
    }

    public Optional<Consumer<MenuActionContext>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        return handlers.containsKey(id);
    }

    /** Every registered action id, sorted: the catalog the in-game action picker offers a spec author. */
    public List<String> ids() {
        return handlers.keySet().stream().sorted().toList();
    }
}
