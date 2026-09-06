package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * One source of menu-item icons. A provider inspects the resolved {@code material} string a menu item declares
 * and, when it recognises that spec, by its own prefix ({@code skull:}, {@code hdb:}, …) or keyword
 * ({@code helmet}, {@code main_hand}, …), returns the base {@link ItemStack} for it. When the spec is not one
 * it owns, it returns {@link Optional#empty()} so the next provider in the chain, and ultimately the renderer's
 * plain-material fallback, gets a turn. A provider therefore must match <em>only</em> its explicit
 * prefixes/keywords and never a bare material name, so an item declaring {@code DIAMOND} or {@code PLAYER_HEAD}
 * is left for the material lookup and renders exactly as before.
 *
 * <p>This is the extension point the later item-plugin integrations implement: an ItemsAdder, Oraxen, Nexo or
 * MMOItems provider claims its own prefix (e.g. {@code itemsadder:<id>}) over its plugin's API, falls back to
 * empty when the plugin is absent, and slots into the same {@link IconProviders} chain.
 *
 * <p>A provider resolves on the calling thread with no I/O and reads only the viewer's own state, so the
 * renderer can invoke it on the viewer's entity thread: Folia-safe, never blocking the main thread.
 */
public interface IconProvider {

    /**
     * The base icon for {@code spec}, or {@link Optional#empty()} when this provider does not recognise it (so
     * the chain falls through to the next provider or the material fallback). The returned stack is the
     * provider's own, callers layer name, lore and decoration onto a copy of it. Never throws: a malformed or
     * unresolvable spec this provider would otherwise own degrades to empty rather than aborting the render.
     */
    Optional<ItemStack> icon(String spec, MenuContext ctx);
}
