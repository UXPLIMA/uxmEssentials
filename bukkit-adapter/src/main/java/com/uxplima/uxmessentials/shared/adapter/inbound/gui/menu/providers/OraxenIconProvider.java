package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an {@code oraxen:<id>} spec to its Oraxen custom item. Oraxen exposes a static facade
 * {@code io.th0rgal.oraxen.api.OraxenItems.getItemById(String)} that returns an Oraxen {@code ItemBuilder} (or
 * {@code null} for an unknown id), whose {@code build()} yields the finished {@link ItemStack}.
 *
 * <p>No {@code io.th0rgal} type is named here (the SDK is reached only by string class-name through reflection) so a
 * server without Oraxen loads none of its classes and the present-guard in {@link ReflectiveItemProvider}
 * short-circuits before any lookup runs.
 */
final class OraxenIconProvider extends ReflectiveItemProvider {

    private static final String PLUGIN_NAME = "Oraxen";
    private static final String PREFIX = "oraxen:";
    private static final String ITEMS_CLASS = "io.th0rgal.oraxen.api.OraxenItems";

    OraxenIconProvider(Server server, Logger log) {
        super(PLUGIN_NAME, PREFIX, server, log);
    }

    @Override
    protected @Nullable ItemStack lookup(String id) throws ReflectiveOperationException {
        Object builder = Class.forName(ITEMS_CLASS)
                .getMethod("getItemById", String.class)
                .invoke(null, id);
        if (builder == null) {
            return null;
        }
        return (ItemStack) builder.getClass().getMethod("build").invoke(builder);
    }
}
