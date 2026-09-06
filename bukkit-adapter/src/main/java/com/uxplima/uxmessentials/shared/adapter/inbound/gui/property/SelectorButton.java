package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;

/**
 * One clickable button in a selector/list child menu: the slot it sits at, the already-built icon to draw there, and
 * the {@link ChildClickHandler} to run when the viewer clicks it. The icon is fully prepared by the caller, name,
 * lore, and any selection glint are baked in, so a {@link SelectorOpener} only places it and wires the click; it
 * makes no presentation decision of its own.
 *
 * <p>The handler receives the click gesture as two booleans, so a list-entry button can branch (left moves up, right
 * moves down, shift-left edits, shift-right removes) exactly as the bespoke entry button did, while a single-gesture
 * button (an enum option, the add or back button) ignores the gesture. Build those through {@link #of}, which adapts
 * a plain {@link Runnable}. This is the unit an {@link EnumProperty} or a {@link ListProperty} hands the opener: a
 * property builds one {@code SelectorButton} per slot and lets the engine paint and route them, keeping the property
 * free of the menu runtime it opens into.
 *
 * @param slot the inventory slot the button is drawn at
 * @param icon the prepared icon to place (name/lore/glint already applied)
 * @param onClick the handler run once when the viewer clicks the button, given the click gesture
 */
@NullMarked
public record SelectorButton(int slot, ItemStack icon, ChildClickHandler onClick) {

    public SelectorButton {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(onClick, "onClick");
    }

    /** A single-gesture button (an option, add, or back) whose action ignores left/right/shift. */
    public static SelectorButton of(int slot, ItemStack icon, Runnable action) {
        return new SelectorButton(slot, icon, ChildClickHandler.ignoring(action));
    }
}
