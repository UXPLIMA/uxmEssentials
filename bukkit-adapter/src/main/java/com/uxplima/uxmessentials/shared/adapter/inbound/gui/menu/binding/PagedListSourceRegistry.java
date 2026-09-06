package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * Holds the functions that supply a list-backed item's entries one page at a time. The viewer's page, sort and
 * filters go down to whatever holds the data, and only that page comes back. This is the streaming sibling of
 * {@link ListSourceRegistry}, for corpora too large to hand over whole. A duplicate id is a wiring mistake, so
 * registration fails loudly rather than letting one source shadow another.
 */
public final class PagedListSourceRegistry {

    private final ConcurrentHashMap<String, BiFunction<MenuContext, PageRequest, PagedResult<?>>> handlers =
            new ConcurrentHashMap<>();

    public void register(String id, BiFunction<MenuContext, PageRequest, PagedResult<?>> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("paged list source already registered: " + id);
        }
    }

    public Optional<BiFunction<MenuContext, PageRequest, PagedResult<?>>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        return handlers.containsKey(id);
    }

    /** Every registered paged-source id, sorted: the catalog a list-backed item's source picker offers. */
    public List<String> ids() {
        return handlers.keySet().stream().sorted().toList();
    }
}
