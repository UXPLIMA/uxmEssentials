package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an {@code mmoitems:<TYPE>:<ID>} spec to its MMOItems item. MMOItems is keyed by a type plus an id, so the
 * bare id here is itself {@code <TYPE>:<ID>} (split on the first colon); a spec missing either half is malformed and
 * yields no item. The item is built through the singleton {@code net.Indyuce.mmoitems.MMOItems} (its public static
 * {@code plugin} field), whose {@code getItem(String type, String id)} returns the built {@link ItemStack} (or
 * {@code null} for an unknown type/id).
 *
 * <p>No {@code net.Indyuce} type is named here (the SDK is reached only by string class-name through reflection) so a
 * server without MMOItems loads none of its classes and the present-guard in {@link ReflectiveItemProvider}
 * short-circuits before any lookup runs.
 */
final class MMOItemsIconProvider extends ReflectiveItemProvider {

    private static final String PLUGIN_NAME = "MMOItems";
    private static final String PREFIX = "mmoitems:";
    private static final String MMOITEMS_CLASS = "net.Indyuce.mmoitems.MMOItems";

    MMOItemsIconProvider(Server server, Logger log) {
        super(PLUGIN_NAME, PREFIX, server, log);
    }

    @Override
    protected @Nullable ItemStack lookup(String id) throws ReflectiveOperationException {
        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1) {
            return null;
        }
        String type = id.substring(0, separator).trim();
        String itemId = id.substring(separator + 1).trim();
        if (type.isEmpty() || itemId.isEmpty()) {
            return null;
        }
        Class<?> mmoItems = Class.forName(MMOITEMS_CLASS);
        Object plugin = mmoItems.getField("plugin").get(null);
        return (ItemStack)
                mmoItems.getMethod("getItem", String.class, String.class).invoke(plugin, type, itemId);
    }
}
