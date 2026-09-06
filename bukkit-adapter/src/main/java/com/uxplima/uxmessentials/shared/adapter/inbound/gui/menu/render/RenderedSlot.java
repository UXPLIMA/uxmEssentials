package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import org.jspecify.annotations.Nullable;

/**
 * What the renderer placed in one inventory slot, handed to the runtime so a click can be routed back to the spec
 * that produced it. {@code item} is the spec actually rendered into the slot, the item itself for a static slot,
 * or the list's template for a list cell, and {@code entry} is the live list element that filled the slot, or
 * {@code null} for a static item. The runtime needs the element so a click on a list cell re-derives the same
 * per-entry context the slot was rendered with.
 */
public record RenderedSlot(MenuItemSpec item, @Nullable Object entry) {}
