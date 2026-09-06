package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * Holds the predicates a spec can name to gate an item's visibility ({@code view}) or a click. A duplicate id is
 * a wiring mistake, so registration fails loudly rather than letting one condition shadow another.
 *
 * <p>A handler receives both the per-open {@link MenuContext} and the condition ref's parsed arguments, mirroring
 * the way an action receives its ref args through {@code MenuActionContext.args()}. That lets a generic condition
 * like {@code perm:some.node} read {@code some.node} from the args; a condition that needs no argument simply
 * ignores the map.
 */
public final class ConditionRegistry {

    private final ConcurrentHashMap<String, BiPredicate<MenuContext, Map<String, String>>> handlers =
            new ConcurrentHashMap<>();

    public void register(String id, BiPredicate<MenuContext, Map<String, String>> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("condition already registered: " + id);
        }
    }

    public Optional<BiPredicate<MenuContext, Map<String, String>>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        return handlers.containsKey(id);
    }

    /** Every registered condition id, sorted: the catalog the in-game requirement picker offers a spec author. */
    public List<String> ids() {
        return handlers.keySet().stream().sorted().toList();
    }
}
