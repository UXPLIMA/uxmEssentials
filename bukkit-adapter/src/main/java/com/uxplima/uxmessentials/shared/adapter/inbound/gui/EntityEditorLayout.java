package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised presentation of an {@link EntityEditorView}: the row count, the ordered slots the property
 * buttons are drawn into (the i-th property goes in the i-th slot), the back button slot, an optional delete
 * button slot, and the back/delete/filler materials. Holds layout integers and materials only, every label,
 * value, and title is a {@code MessageKey} in the catalog, so an operator-edited layout and a translated
 * catalog never collide.
 *
 * <p>Built once at load time by {@link GuiLayouts#loadEntityEditor}; the editor reads {@link #propertySlots}
 * to place and to resolve a clicked slot back to its property. The delete slot is optional: an editor for a
 * non-deletable entity omits it from the conf and the editor shows no delete button.
 *
 * @param rows the editor row count, 1..6
 * @param propertySlots the slots property buttons fill, in property order
 * @param backSlot the slot the back button sits in
 * @param deleteSlot the slot the delete button sits in, or empty for a non-deletable entity
 * @param backIcon the back button material
 * @param deleteIcon the delete button material; meaningful only when {@link #deleteSlot} is present
 * @param filler the background filler material
 */
@NullMarked
public record EntityEditorLayout(
        int rows,
        List<Integer> propertySlots,
        int backSlot,
        OptionalInt deleteSlot,
        Material backIcon,
        Material deleteIcon,
        Material filler) {

    public EntityEditorLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        propertySlots = List.copyOf(Objects.requireNonNull(propertySlots, "propertySlots"));
        if (propertySlots.isEmpty()) {
            throw new IllegalArgumentException("propertySlots must not be empty");
        }
        int size = rows * 9;
        for (int slot : propertySlots) {
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("property slot out of range: " + slot);
            }
        }
        if (backSlot < 0 || backSlot >= size) {
            throw new IllegalArgumentException("backSlot out of range: " + backSlot);
        }
        Objects.requireNonNull(deleteSlot, "deleteSlot");
        deleteSlot.ifPresent(slot -> {
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("deleteSlot out of range: " + slot);
            }
        });
        Objects.requireNonNull(backIcon, "backIcon");
        Objects.requireNonNull(deleteIcon, "deleteIcon");
        Objects.requireNonNull(filler, "filler");
    }

    /** A code default: a 3-row editor with a back button and no delete, used when no conf is present. */
    public static EntityEditorLayout codeDefault(List<Integer> propertySlots, int backSlot) {
        return new EntityEditorLayout(
                3,
                propertySlots,
                backSlot,
                OptionalInt.empty(),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /** A code default that includes a delete button at {@code deleteSlot}. */
    public static EntityEditorLayout withDelete(List<Integer> propertySlots, int backSlot, int deleteSlot) {
        return new EntityEditorLayout(
                3,
                propertySlots,
                backSlot,
                OptionalInt.of(deleteSlot),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }
}
