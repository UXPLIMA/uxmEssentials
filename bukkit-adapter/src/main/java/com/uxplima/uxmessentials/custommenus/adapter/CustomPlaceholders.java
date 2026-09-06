package com.uxplima.uxmessentials.custommenus.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import org.jspecify.annotations.NullMarked;

/**
 * Backs the operator's global {@code menus/placeholders.conf}: a map of custom {@code %name%} tokens to template
 * strings any spec's name, lore or title can reference. Registered once as a placeholder fallback over the shared
 * {@link MenuBindings}, so a {@code %welcome%} spec both validates and resolves the same way the built-in and
 * {@code data_*} tokens do, without a code handler per name.
 *
 * <p>The definitions live behind an {@link AtomicReference} rather than a set of exact handlers for one reason: a
 * handler throws on a duplicate id and cannot be un-registered, so a {@code /menu reload} could neither replace nor
 * drop one. A single fallback whose {@code claims} and {@code resolve} read the live map lets a reload swap the whole
 * map in one write with no re-registration and no duplicate, a reader sees either the old or the new map, never a
 * half-applied one. The fallback is registered <em>after</em> the {@code papi_*}/{@code data_*}/{@code stat_*}
 * families (see the wiring), so an operator who misnames a custom placeholder with a reserved prefix is still claimed
 * by that prefix's fallback first, and a custom name can never shadow a built-in exact handler, since the registry
 * consults exact handlers before any fallback: built-ins always win.
 *
 * <p>A template may reference other tokens: a built-in ({@code %player%}), a data reader ({@code %data_number_coins%}),
 * a PlaceholderAPI value ({@code %papi_*%}), or another custom name. {@link #substitute} expands those inner
 * {@code %token%}s through the same registry, but deliberately leaves {@code {math: …}} blocks and MiniMessage
 * {@code <tags>} verbatim: the outer {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer}
 * owns the math and MiniMessage passes, so a template like {@code "{math: %coins% * 2}"} has only its inner
 * {@code %coins%} resolved here, and the outer pass then evaluates the arithmetic. Recursion is bounded by a
 * per-render depth guard so a cycle ({@code a=%b%}, {@code b=%a%}) stops at {@link #MAX_DEPTH} and returns the
 * template unexpanded rather than overflowing the stack.
 */
@NullMarked
public final class CustomPlaceholders {

    /** A single {@code %token%} in a template; {@code group(1)} is the bare id, matching the registry's own grammar. */
    private static final Pattern TOKEN = Pattern.compile("%([a-zA-Z0-9_]+)%");

    /** The deepest a template may nest custom references before expansion stops; a cycle simply hits this and returns. */
    private static final int MAX_DEPTH = 8;

    /**
     * Per-render recursion depth. A render is synchronous on one region/entity thread, so a thread-local counter is
     * the right scope: it isolates concurrent renders on different threads and needs no lock. It is incremented on
     * each {@link #substitute} entry and decremented in a {@code finally}, so an expansion that throws still unwinds
     * the count cleanly.
     */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private final MenuBindings bindings;

    /**
     * The operator's {@code name -> template} definitions, seeded empty so nothing is claimed until the placeholders
     * file is read, and swapped whole on reload. An atomic reference keeps the swap a single publish that a concurrent
     * render observes as either the old or the new map.
     */
    private final AtomicReference<Map<String, String>> defs = new AtomicReference<>(Map.of());

    public CustomPlaceholders(MenuBindings bindings) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        bindings.placeholders().fallback(this::claims, this::resolve);
    }

    /**
     * Swap the custom-placeholder definitions atomically. Called once per load pass, so a {@code /menu reload}
     * re-reads {@code placeholders.conf} and republishes the map without re-registering the fallback (which would
     * duplicate it). A name dropped by the new map stops claiming; a name added starts claiming and validating.
     */
    public void reload(Map<String, String> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        defs.set(Map.copyOf(definitions));
    }

    /** Whether {@code id} names a currently defined custom placeholder, read off the live map so a reload takes effect. */
    private boolean claims(String id) {
        return definitions().containsKey(id);
    }

    /**
     * Expand the template a claimed custom placeholder maps to. The map is read once so a concurrent reload cannot
     * null the template mid-resolve; a template that vanished in that race resolves to empty, matching the renderer's
     * blank-on-unknown stance rather than throwing into a render.
     */
    private String resolve(String id, MenuContext ctx) {
        String template = definitions().get(id);
        return template == null ? "" : substitute(template, ctx);
    }

    /** The current definitions; the reference is seeded and only ever set non-null, so the read is never null. */
    private Map<String, String> definitions() {
        return Objects.requireNonNull(defs.get(), "defs");
    }

    /**
     * Expand every {@code %token%} in {@code template} through the shared registry, leaving {@code {math: …}} blocks
     * and MiniMessage {@code <tags>} verbatim for the outer renderer's own passes. An unknown token blanks, exactly as
     * the renderer's own substitution does, so a template reads the same whichever path expands it. The depth guard
     * bounds recursion: once nesting reaches {@link #MAX_DEPTH} the template is returned unexpanded, so a self- or
     * mutually-referencing custom placeholder terminates instead of overflowing the stack.
     */
    private String substitute(String template, MenuContext ctx) {
        int[] depth = DEPTH.get();
        if (depth[0] >= MAX_DEPTH) {
            return template;
        }
        depth[0]++;
        try {
            Matcher matcher = TOKEN.matcher(template);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String value = bindings.placeholders()
                        .resolveOrReport(matcher.group(1), ctx)
                        .orElse("");
                matcher.appendReplacement(out, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(out);
            return out.toString();
        } finally {
            depth[0]--;
        }
    }
}
