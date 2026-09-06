package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import org.jspecify.annotations.NullMarked;

/**
 * What a {@link SelectorButton} runs when the viewer clicks it, given the click gesture as two plain booleans rather
 * than the engine's click-kind enum. A single-gesture button (an enum option, the add button, the back button)
 * ignores both flags; a list-entry button branches on them exactly as the bespoke entry button branched on the live
 * {@code InventoryClickEvent} (left moves up, right moves down, shift-left edits, shift-right removes).
 *
 * <p>Passing the gesture as the property model's own booleans, not the engine's {@code ClickKind}, is what keeps
 * the property package decoupled from the menu engine: the engine maps its click kind down to these two booleans when
 * it invokes a button, so neither this type nor any property ever names an engine class.
 */
@NullMarked
@FunctionalInterface
public interface ChildClickHandler {

    /**
     * Run the button's action for a click whose gesture is described by {@code rightClick} (right vs left) and
     * {@code shiftClick} (whether shift was held). A button that does not branch on the gesture simply ignores both.
     */
    void onClick(boolean rightClick, boolean shiftClick);

    /** Adapt a gesture-agnostic action into a handler that ignores the click gesture (an option/add/back button). */
    static ChildClickHandler ignoring(Runnable action) {
        java.util.Objects.requireNonNull(action, "action");
        return (rightClick, shiftClick) -> action.run();
    }
}
