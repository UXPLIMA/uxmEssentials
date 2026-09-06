package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Paints the engine's two-button confirm window, matching the geometry of the uxmLib {@code ConfirmMenu} it
 * replaces: a three-row inventory with a lime-wool yes button at slot 11 and a red-wool no button at slot 15, so a
 * migrated caller meets the same window it always did. The decision carried by each button, what yes and no run,
 * is the {@code ConfirmState} on the holder; this renderer only places the icons and reports the two slots it used,
 * which the façade records onto that state so the one listener can route a click back to the right decision.
 *
 * <p>The window's meaning is the title the caller resolved from a {@code MessageKey}; the buttons themselves carry
 * no inline text (an empty name), so the confirm window adds no user-facing literal of its own.
 */
@NullMarked
public final class ConfirmRenderer {

    /** The three-row geometry the uxmLib confirm window used, so a migrated caller meets an identical layout. */
    public static final int ROWS = 3;

    /** The yes (confirm) button slot, byte-for-byte the uxmLib {@code ConfirmMenu} confirm slot. */
    public static final int YES_SLOT = 11;

    /** The no (cancel) button slot, byte-for-byte the uxmLib {@code ConfirmMenu} cancel slot. */
    public static final int NO_SLOT = 15;

    public ConfirmRenderer() {}

    /** Place the lime yes button at {@link #YES_SLOT} and the red no button at {@link #NO_SLOT} in {@code inv}. */
    public void populate(Inventory inv) {
        inv.setItem(YES_SLOT, button(Material.LIME_WOOL));
        inv.setItem(NO_SLOT, button(Material.RED_WOOL));
    }

    private static ItemStack button(Material material) {
        return ItemBuilder.of(material).name(Component.empty()).build();
    }
}
