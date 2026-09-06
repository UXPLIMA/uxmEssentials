package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Paints the engine's selector child window: the caller's filler everywhere, then each option's already-built icon
 * at its slot. Like the confirm renderer it is stateless and places only icons, the choose action each button
 * carries lives on the holder's {@code SelectorState}, which the façade records so the one listener can route a
 * click back to the right option. The option icons are prepared by the property (name, lore, and selection glint
 * baked in), so this renderer makes no presentation decision and adds no user-facing text of its own.
 */
@NullMarked
public final class SelectorRenderer {

    public SelectorRenderer() {}

    /** Fill {@code inv} with {@code filler}, then draw each option button at its slot. */
    public void populate(Inventory inv, Material filler, List<SelectorButton> buttons) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(filler, "filler");
        Objects.requireNonNull(buttons, "buttons");
        ItemStack fillerItem = ItemBuilder.of(filler).name(Component.empty()).build();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, fillerItem);
        }
        for (SelectorButton button : buttons) {
            inv.setItem(button.slot(), button.icon());
        }
    }
}
