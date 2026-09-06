package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;

/**
 * Saves the bespoke {@code /kit editor} item-grid window back to its kit when the editor closes it. Every other kit
 * administration GUI, the manager list, both category GUIs, the kit→category selector, and the per-kit settings
 * panel. Now renders through the menu engine and is routed by the engine's own MenuListener, so this listener owns
 * only the one still-bespoke window: the editable six-row item grid {@link KitEditorView} opens, recognised by its
 * {@link KitEditorHolder}. The grid is a true item container (the editor drags stacks in and out of it), the one
 * leaf the menu engine deliberately does not model, so it keeps its own close handler.
 */
@NullMarked
public final class KitEditorListener implements Listener {

    private final KitEditorView editorView;

    public KitEditorListener(KitEditorView editorView) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof KitEditorHolder editorHolder) {
            editorView.onClose(editorHolder);
        }
    }
}
