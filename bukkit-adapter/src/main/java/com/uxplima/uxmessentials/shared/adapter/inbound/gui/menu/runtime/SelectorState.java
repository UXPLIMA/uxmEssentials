package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ChildClickHandler;
import org.jspecify.annotations.NullMarked;

/**
 * The per-open state of a selector child window, parked on its {@link MenuHolder} on the selector path only. Where a
 * spec menu routes a click through its {@code clickMap}, an editor through its {@link EditorState}, and a confirm
 * through its {@link ConfirmState}, a selector routes through this: each option slot maps to the choose action drawn
 * there, and the listener consults {@link #chooseAt} to find the one the player clicked. Keeping these actions here
 * off the spec {@code clickMap} and off the editor maps, is what lets the one listener tell a selector apart from
 * the other three menu kinds without giving any of them a slot it has no item for.
 *
 * <p>Clicking a button closes the window and runs its handler (which reopens the parent editor, or, for a list
 * child, reopens the list itself after a mutation), so a single-fire guard is belt-and-braces against a stray second
 * click arriving in the same tick before the close lands: {@link #fire} flips the guard and reports whether this is
 * the first click, so the listener runs a handler at most once even if it is clicked twice. Each handler receives the
 * click gesture, so a list-entry button can branch on left/right/shift while an option/add/back button ignores it.
 */
@NullMarked
public final class SelectorState {

    private final Map<Integer, ChildClickHandler> choices = new HashMap<>();

    private boolean fired;

    public SelectorState(Map<Integer, ChildClickHandler> choices) {
        Objects.requireNonNull(choices, "choices");
        choices.forEach((slot, choice) -> this.choices.put(slot, Objects.requireNonNull(choice, "choice")));
    }

    /** The click handler drawn at {@code slot}, or empty when the slot carries no button. */
    public Optional<ChildClickHandler> chooseAt(int slot) {
        return Optional.ofNullable(choices.get(slot));
    }

    /** Flip the single-fire guard, returning whether this is the first choice so the listener runs it at most once. */
    public boolean fire() {
        if (fired) {
            return false;
        }
        fired = true;
        return true;
    }
}
