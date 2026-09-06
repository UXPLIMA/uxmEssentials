package com.uxplima.uxmessentials.homes.domain;

/**
 * A home's position within the owner's ordered slot grid. A zero-based index that doubles as the
 * DB primary key component and the GUI cell index. The 1-based {@link #displayNumber()} is what the
 * player sees; internally everything is zero-based.
 *
 * @param index zero-based slot index (non-negative)
 */
public record HomeSlot(int index) {

    public HomeSlot {
        if (index < 0) {
            throw new IllegalArgumentException("home slot must not be negative: " + index);
        }
    }

    /** Returns a slot for the given zero-based {@code index}. */
    public static HomeSlot of(int index) {
        return new HomeSlot(index);
    }

    /** Returns {@code index + 1}: the 1-based number shown to the player in menus and messages. */
    public int displayNumber() {
        return index + 1;
    }
}
