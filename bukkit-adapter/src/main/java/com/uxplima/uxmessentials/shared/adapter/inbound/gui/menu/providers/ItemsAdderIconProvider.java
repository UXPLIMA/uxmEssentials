package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an {@code itemsadder:<id>} spec to its ItemsAdder custom item. ItemsAdder exposes a static factory
 * {@code dev.lone.itemsadder.api.CustomStack.getInstance(String)} that returns a {@code CustomStack} wrapper (or
 * {@code null} for an unknown id), whose {@code getItemStack()} hands back the built {@link ItemStack}.
 *
 * <p>No {@code dev.lone} type is named here (the SDK is reached only by string class-name through reflection) so a
 * server without ItemsAdder loads none of its classes and the present-guard in {@link ReflectiveItemProvider}
 * short-circuits before any lookup runs.
 */
final class ItemsAdderIconProvider extends ReflectiveItemProvider {

    private static final String PLUGIN_NAME = "ItemsAdder";
    private static final String PREFIX = "itemsadder:";
    private static final String CUSTOM_STACK_CLASS = "dev.lone.itemsadder.api.CustomStack";

    ItemsAdderIconProvider(Server server, Logger log) {
        super(PLUGIN_NAME, PREFIX, server, log);
    }

    @Override
    protected @Nullable ItemStack lookup(String id) throws ReflectiveOperationException {
        Object custom = Class.forName(CUSTOM_STACK_CLASS)
                .getMethod("getInstance", String.class)
                .invoke(null, id);
        if (custom == null) {
            return null;
        }
        return (ItemStack) custom.getClass().getMethod("getItemStack").invoke(custom);
    }
}
