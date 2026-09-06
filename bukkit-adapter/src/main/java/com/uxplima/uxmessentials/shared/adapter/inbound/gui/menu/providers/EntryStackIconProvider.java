package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * Renders a list entry that is itself an {@link ItemStack} as its own icon. The spec is the exact marker
 * {@code entry}, matched case-insensitively after trimming; any other spec is not claimed, so a real material
 * name or another provider's prefix falls straight through untouched. When the marker is claimed, the provider
 * reads the currently-bound list element from {@link MenuContext#entry()} and, if that element is an
 * {@link ItemStack}, returns a clone of it, so a list source that yields raw stacks (a viewer's own inventory,
 * an ender chest) draws each cell as the actual item with no per-item material spec.
 *
 * <p>The returned stack is always a clone, so the name, lore and decor the renderer layers on top can never
 * reach the backing item: the tile is a read-only view. When no entry is bound, or the bound entry is not an
 * {@link ItemStack} (the marker was written off such a list), the provider yields {@link Optional#empty()} so
 * the spec degrades to the material fallback rather than throwing. Nothing here mutates any inventory; it only
 * reflects stacks a source already captured.
 */
final class EntryStackIconProvider implements IconProvider {

    private static final String MARKER = "entry";

    @Override
    public Optional<ItemStack> icon(String spec, MenuContext ctx) {
        if (!spec.trim().equalsIgnoreCase(MARKER)) {
            return Optional.empty();
        }
        return ctx.entry()
                .filter(ItemStack.class::isInstance)
                .map(ItemStack.class::cast)
                .map(ItemStack::clone);
    }
}
