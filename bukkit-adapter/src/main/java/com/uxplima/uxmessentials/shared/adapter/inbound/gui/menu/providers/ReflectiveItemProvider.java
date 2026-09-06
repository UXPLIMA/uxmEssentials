package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Shared scaffolding for the icon providers that resolve a custom-item id through another plugin reached purely by
 * reflection: ItemsAdder, Oraxen, Nexo, CraftEngine, MMOItems. A subclass names the plugin it integrates with and
 * its {@code material} prefix (e.g. {@code itemsadder:}) and implements one reflective primitive: turn the bare id
 * into an {@link ItemStack}. This base owns the load-safe contract around it. A spec it does not own (the prefix does not
 * match) is left for the next provider; a matching spec is gated by the plugin-present guard, so a server without the
 * plugin resolves to empty: the renderer's plain-material fallback then renders the id as a material name. Any
 * {@link ReflectiveOperationException} (the API absent, or its shape shifted under a version bump) or unchecked
 * failure from the lookup is logged exactly once and degraded to empty rather than aborting the render.
 *
 * <p>This is the same discipline the migration {@code PlayerPointsBalanceFeed} uses. Crucially, a subclass names
 * the integrated plugin's SDK only by string class-name through {@link
 * Class#forName(String)} and reflective lookups, so no field or method signature here carries an SDK type:
 * constructing one of these on a server without the plugin loads none of its classes, and the present-guard
 * short-circuits before any reflection runs.
 */
abstract class ReflectiveItemProvider implements IconProvider {

    private final String pluginName;

    /** The {@code material} prefix this provider claims, stored lower-cased for a case-insensitive match. */
    private final String prefix;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    ReflectiveItemProvider(String pluginName, String prefix, Server server, Logger log) {
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.prefix = Objects.requireNonNull(prefix, "prefix").toLowerCase(Locale.ROOT);
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public final Optional<ItemStack> icon(String spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        String trimmed = spec.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            return Optional.empty();
        }
        String id = trimmed.substring(prefix.length()).trim();
        // Plugin absent (or a blank id) is a silent miss, not a warning: the menu degrades to the material fallback.
        if (id.isBlank() || !server.getPluginManager().isPluginEnabled(pluginName)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(lookup(id));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // A version bump can shift the API shape and surface as either a reflective miss or an unchecked
            // failure from inside the call; both degrade to the material fallback after one warning.
            degrade(failure);
            return Optional.empty();
        }
    }

    /**
     * Resolve {@code id} to its {@link ItemStack} through the integrated plugin's API reflectively; called only past
     * the present-guard. An unknown id returns {@code null} (the SDK's own "no such item"), which the base maps to
     * empty so the spec falls through to the material fallback.
     */
    protected abstract @Nullable ItemStack lookup(String id) throws ReflectiveOperationException;

    /** Log the first reflective failure for this provider; subsequent ones stay quiet to avoid log spam. */
    private void degrade(Exception failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=item_provider_reflection_failed plugin={} reason={}", pluginName, failure.toString());
        }
    }
}
