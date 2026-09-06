package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;

/**
 * Turns a {@code b64:<base64>} material spec into the fully-deserialized {@link ItemStack} it encodes, every
 * component, enchantment, name, and lore the operator captured. This is the same {@code b64:} codec the give
 * actions and NPC equipment use ({@link SerializedItems}), reused here so the marker lives in exactly one place.
 *
 * <p>The stack is returned as the base icon, so the item's name, lore, and decor layer on top of it exactly as
 * they do over a material or a skull. With {@code lore-mode = append} the stack's own lore is preserved and the
 * spec lore is added beneath it. A token whose base64 or NBT is corrupt decodes to {@link Optional#empty()}, so
 * the spec falls through to the renderer's material fallback (a plain STONE) rather than aborting the render.
 *
 * <p>The provider claims only the {@code b64:} prefix, never a bare material name.
 */
final class SerializedStackIconProvider implements IconProvider {

    @Override
    public Optional<ItemStack> icon(String spec, MenuContext ctx) {
        String trimmed = spec.trim();
        if (!SerializedItems.isSerialized(trimmed)) {
            return Optional.empty();
        }
        return SerializedItems.decode(trimmed);
    }
}
