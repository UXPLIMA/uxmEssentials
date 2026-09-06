package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * Holds the functions that expand a spec's {@code %token%} placeholders into text at render time. A duplicate id
 * is a wiring mistake, so registration fails loudly rather than letting one token resolver overwrite another.
 *
 * <p>Besides id-keyed handlers, the registry carries one or more optional {@link Fallback}s: a resolver that claims
 * a family of ids by predicate rather than by exact name, so a whole prefix (the {@code papi_*} PlaceholderAPI
 * bridge, the {@code data_*}/{@code meta_*} player-data readers) resolves without a handler per token. Fallbacks are
 * consulted only when no exact handler matches, in registration order, the first that claims an id wins, and
 * {@link #has} treats an id any fallback claims as known so {@link MenuBindings#validate} accepts a {@code %papi_*%}
 * or {@code %data_value_*%} spec.
 */
public final class PlaceholderRegistry {

    private static final Logger LOG = Logger.getLogger(PlaceholderRegistry.class.getName());

    private final ConcurrentHashMap<String, Function<MenuContext, String>> handlers = new ConcurrentHashMap<>();

    /**
     * The ids already reported as broken, so a handler that throws on every render of an {@code update = true}
     * item costs one console line rather than one per tick. A failure that keeps happening says nothing the
     * first line did not.
     */
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    /**
     * The prefix/family resolvers consulted when no exact handler matches, in registration order. Written once each
     * at wiring time and read on every render, so a copy-on-write list keeps the render-time reads lock-free.
     */
    private final CopyOnWriteArrayList<Fallback> fallbacks = new CopyOnWriteArrayList<>();

    public void register(String id, Function<MenuContext, String> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("placeholder already registered: " + id);
        }
    }

    /**
     * Append a fallback resolver. {@code claims} decides which ids the {@code resolve} function owns
     * (e.g. {@code id -> id.startsWith("papi_")}); {@code resolve} expands a claimed id against the open context.
     * More than one fallback may coexist for disjoint prefix families (PlaceholderAPI and the player-data readers);
     * when two overlap on an id, the first registered wins, so registration order is the tie-break.
     */
    public void fallback(Predicate<String> claims, BiFunction<String, MenuContext, String> resolve) {
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(resolve, "resolve");
        fallbacks.add(new Fallback(claims, resolve));
    }

    public Optional<Function<MenuContext, String>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(handlers.get(id));
    }

    /**
     * Resolve {@code id} against {@code ctx}: the exact handler if one is registered, else the first fallback that
     * claims the id, else empty. This is the single seam the renderer substitutes a {@code %token%} through, so a
     * {@code %papi_*%} token resolves through the PlaceholderAPI fallback and a {@code %data_value_*%} token through
     * the player-data fallback, while a plain {@code %page%} resolves through its exact handler.
     */
    public Optional<String> resolve(String id, MenuContext ctx) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ctx, "ctx");
        Function<MenuContext, String> handler = handlers.get(id);
        if (handler != null) {
            return Optional.ofNullable(handler.apply(ctx));
        }
        for (Fallback fallback : fallbacks) {
            if (fallback.claims().test(id)) {
                return Optional.ofNullable(fallback.resolve().apply(id, ctx));
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve {@code id} the way a render does: as {@link #resolve}, except that a handler which throws yields no
     * text and is named on the console once instead of escaping. This is the seam every render-time and
     * condition-time caller uses, because the alternative is what used to happen: one broken handler took the
     * whole window with it, so a bad value an operator typed cost one icon while a bad handler a consumer wired
     * through {@code registerPlaceholder} cost the menu.
     *
     * <p>{@link #resolve} keeps throwing on purpose. A caller that would rather know is entitled to know, and
     * the developer API hands third parties a way to register a handler we do not own, so the price of one
     * failing has to be paid where the render can carry on rather than decided once for everybody.
     *
     * <p>The silence in {@link #resolveAll} is a different case and not an inconsistency: that call runs every
     * handler against a context most of them were not written for, so a throw there is expected and means only
     * that the placeholder belongs to another menu. Here the token was asked for by name, so a throw is a defect
     * in whoever registered it, and a blank token tells its author nothing.
     */
    public Optional<String> resolveOrReport(String id, MenuContext ctx) {
        try {
            return resolve(id, ctx);
        } catch (RuntimeException failure) {
            if (reported.add(id)) {
                LOG.log(Level.WARNING, "event=placeholder_failed token=" + id, failure);
            }
            return Optional.empty();
        }
    }

    /**
     * Every exactly-registered placeholder id, sorted: the catalog a token picker offers. The prefix/family
     * fallbacks ({@code papi_*}, {@code data_*}) claim ids by predicate, not by name, so they cannot be enumerated
     * here; a picker offers the exact tokens and leaves the open-ended families to a typed token.
     */
    public List<String> ids() {
        return handlers.keySet().stream().sorted().toList();
    }

    public boolean has(String id) {
        Objects.requireNonNull(id, "id");
        if (handlers.containsKey(id)) {
            return true;
        }
        for (Fallback fallback : fallbacks) {
            if (fallback.claims().test(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve every registered placeholder against {@code ctx} into an {@code id -> value} map, so a catalog
     * {@code @key} text can fill its {@code {token}} arguments from the same placeholders a {@code %token%}
     * spec uses. A resolver that throws because this context does not carry what it needs (a placeholder owned
     * by a different menu) is skipped rather than aborting the render. The token it would fill simply stays
     * unresolved, the same fail-soft stance the rest of the renderer takes.
     */
    public Map<String, String> resolveAll(MenuContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Map<String, String> resolved = new HashMap<>();
        handlers.forEach((id, handler) -> {
            try {
                String value = handler.apply(ctx);
                if (value != null) {
                    resolved.put(id, value);
                }
            } catch (RuntimeException notApplicableHere) {
                // This placeholder belongs to a context shape this menu does not have; leave its token unfilled.
            }
        });
        return resolved;
    }

    /** The prefix/family fallback: a predicate over the ids it owns and the resolver that expands one of them. */
    private record Fallback(Predicate<String> claims, BiFunction<String, MenuContext, String> resolve) {}
}
