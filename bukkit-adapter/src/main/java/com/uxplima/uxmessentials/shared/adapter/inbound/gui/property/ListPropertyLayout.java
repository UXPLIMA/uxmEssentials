package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised geometry and materials of a {@link ListProperty}'s sub-menu, read once from a module's
 * {@code gui/*.conf}: the row count, the slots each entry button is drawn into, the add and back button slots,
 * and the entry/add/back/filler icon materials. Holds layout integers and materials only: never localised
 * strings (those are a {@link ListPropertyText}), so an operator-edited layout and a translated catalog never
 * collide. Validated at construction; a view passes the parsed record straight to the property.
 *
 * @param rows the sub-menu row count, 1..6
 * @param entrySlots the slots entry buttons fill, in order; paging is the caller's concern if it overflows
 * @param addSlot the slot the add button sits in
 * @param backSlot the slot the back button sits in
 * @param entryIcon the per-entry button material
 * @param addIcon the add button material
 * @param backIcon the back button material
 * @param fillerIcon the background filler material
 */
@NullMarked
public record ListPropertyLayout(
        int rows,
        List<Integer> entrySlots,
        int addSlot,
        int backSlot,
        Material entryIcon,
        Material addIcon,
        Material backIcon,
        Material fillerIcon) {

    public ListPropertyLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        entrySlots = List.copyOf(Objects.requireNonNull(entrySlots, "entrySlots"));
        if (entrySlots.isEmpty()) {
            throw new IllegalArgumentException("entrySlots must not be empty");
        }
        if (addSlot < 0 || addSlot >= rows * 9) {
            throw new IllegalArgumentException("addSlot out of range: " + addSlot);
        }
        if (backSlot < 0 || backSlot >= rows * 9) {
            throw new IllegalArgumentException("backSlot out of range: " + backSlot);
        }
        Objects.requireNonNull(entryIcon, "entryIcon");
        Objects.requireNonNull(addIcon, "addIcon");
        Objects.requireNonNull(backIcon, "backIcon");
        Objects.requireNonNull(fillerIcon, "fillerIcon");
    }
}
