package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.LayoutFit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EditorSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.EditorState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Lays an {@link EditorSpec} into an open inventory for one viewer, the editor counterpart of {@link MenuRenderer}.
 * It paints the layout's filler everywhere, then one button per {@link EditableProperty} at the layout's ordered
 * property slots, then the back button, then the optional delete button, recording each clickable slot onto the
 * holder's {@link EditorState} so the one listener can route a later click back to the property or button drawn
 * there. The property list is re-read fresh from the subject on every call (a page-less re-render of an editor is
 * just a repaint), so a value changed by a click shows on the next draw.
 *
 * <p>The per-property button is rendered byte-for-byte as the bespoke {@code EntityEditorView} renders it, the
 * property's own icon, its {@code label()} catalog name, and the value-lore catalog line wrapping
 * {@code valueLore(viewer)}, so a later shim that re-points the view at the engine produces a slot-for-slot
 * identical window.
 */
@NullMarked
public final class EditorRenderer {

    private final GuiText guiText;

    public EditorRenderer(GuiText guiText) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
    }

    /**
     * Fill {@code inv} with the editor {@code spec} resolves to for {@code viewer} editing {@code state}'s subject,
     * recording every clickable slot onto {@code state}. The caller clears the editor's slot routing first, so this
     * never needs to; it just records the slots it paints.
     */
    public void populate(Inventory inv, EditorSpec spec, EditorState state, Player live, PlayerRef viewer) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(viewer, "viewer");
        EntityEditorLayout layout = spec.layout();
        fill(inv, layout);
        paintProperties(inv, spec, state, viewer);
        paintBack(inv, spec, state, live, viewer);
        paintDelete(inv, spec, state, live);
    }

    /** Paint one button per property at the layout's ordered slots, recording each as a property slot. */
    private void paintProperties(Inventory inv, EditorSpec spec, EditorState state, PlayerRef viewer) {
        List<EditableProperty> props = spec.propertiesFor(state.subject());
        List<Integer> slots = spec.layout().propertySlots();
        int drawn = LayoutFit.drawable("editor property-slots", props.size(), slots);
        for (int i = 0; i < drawn; i++) {
            EditableProperty property = props.get(i);
            int slot = slots.get(i);
            inv.setItem(slot, propertyButton(viewer, spec, property));
            state.recordProperty(slot, property);
        }
    }

    /**
     * One property's button: the property icon, the shared value-lore, and the property's own label as the title
     * on the first lore line, which is where the canon keeps a tile's title. The label used to be the display
     * name, which put it outside the tooltip's body and left the lore opening on the generic word every property
     * shares ("setting") rather than on the setting the player is looking at.
     */
    private ItemStack propertyButton(PlayerRef viewer, EditorSpec spec, EditableProperty property) {
        List<Component> lore =
                List.of(guiText.text(viewer, spec.valueLore(), Map.of("value", property.valueLore(viewer))));
        return ItemBuilder.of(property.icon())
                .name(Tiles.blankName())
                .lore(Tiles.titled(guiText.text(viewer, property.label()), lore))
                .build();
    }

    /** Paint the back button and record it as a plain-button slot whose click runs the spec's back callback. */
    private void paintBack(Inventory inv, EditorSpec spec, EditorState state, Player live, PlayerRef viewer) {
        EntityEditorLayout layout = spec.layout();
        ItemStack back = ItemBuilder.of(layout.backIcon())
                .name(guiText.text(viewer, spec.backName()))
                .build();
        inv.setItem(layout.backSlot(), back);
        state.recordButton(layout.backSlot(), () -> spec.onBack().accept(live, viewer));
    }

    /**
     * Paint the optional delete button when the spec carries one, recording its click as a plain button. The click
     * runs the spec's delete handler directly; gating it behind a confirm menu is the confirm increment's seam, so
     * an editor opened before that increment lands carries no consumer that relies on the gate.
     */
    private void paintDelete(Inventory inv, EditorSpec spec, EditorState state, Player live) {
        if (!spec.hasDelete()) {
            return;
        }
        EntityEditorLayout layout = spec.layout();
        PlayerRef viewer = new PlayerRef(live.getUniqueId(), live.getName());
        ItemStack delete = ItemBuilder.of(layout.deleteIcon())
                .name(guiText.text(viewer, spec.deleteName().orElseThrow()))
                .build();
        int slot = layout.deleteSlot().getAsInt();
        inv.setItem(slot, delete);
        state.recordButton(slot, () -> spec.onDelete().orElseThrow().accept(live, state.subject()));
    }

    /** Fill every slot with the layout's filler so no vanilla slot shows through behind the buttons. */
    private void fill(Inventory inv, EntityEditorLayout layout) {
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            inv.setItem(slot, filler);
        }
    }
}
