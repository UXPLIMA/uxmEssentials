package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * Holds the functions that supply the entries a list-backed item expands over (the warps to show, the homes to
 * list). A duplicate id is a wiring mistake, so registration fails loudly rather than letting one source shadow
 * another.
 */
public final class ListSourceRegistry {

    private final ConcurrentHashMap<String, Function<MenuContext, List<?>>> handlers = new ConcurrentHashMap<>();

    public void register(String id, Function<MenuContext, List<?>> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("list source already registered: " + id);
        }
    }

    public Optional<Function<MenuContext, List<?>>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        return handlers.containsKey(id);
    }

    /** Every registered list-source id, sorted: the catalog a list-backed item's source picker offers. */
    public List<String> ids() {
        return handlers.keySet().stream().sorted().toList();
    }
}
