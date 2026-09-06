package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * The per-open state of a two-button confirm window, parked on its {@link MenuHolder} on the confirm path only.
 * Where a spec menu routes a click through its {@code clickMap} and an editor routes one through its
 * {@link EditorState}, a confirm routes through this: the yes slot maps to one decision runnable and the no slot to
 * another, and the listener consults {@link #decisionAt} to find the one the player clicked. Keeping the two
 * decisions here (off the spec {@code clickMap} and off the editor maps) is what lets the one listener tell a
 * confirm window apart from the other two without giving either a slot it has no item for.
 *
 * <p>The window closes on the first click, so a single-fire guard is belt-and-braces against a stray second click
 * arriving in the same tick before the close lands: {@link #fire} flips the guard and reports whether this is the
 * first decision, so the listener runs a handler at most once even if it is clicked twice.
 */
@NullMarked
public final class ConfirmState {

    private final int yesSlot;
    private final int noSlot;
    private final Runnable onYes;
    private final Runnable onNo;

    private boolean fired;

    public ConfirmState(int yesSlot, int noSlot, Runnable onYes, Runnable onNo) {
        this.yesSlot = yesSlot;
        this.noSlot = noSlot;
        this.onYes = Objects.requireNonNull(onYes, "onYes");
        this.onNo = Objects.requireNonNull(onNo, "onNo");
    }

    /** The decision runnable drawn at {@code slot} (yes or no), or empty when the slot carries neither button. */
    public Optional<Runnable> decisionAt(int slot) {
        if (slot == yesSlot) {
            return Optional.of(onYes);
        }
        if (slot == noSlot) {
            return Optional.of(onNo);
        }
        return Optional.empty();
    }

    /** Flip the single-fire guard, returning whether this is the first decision so the listener runs it at most once. */
    public boolean fire() {
        if (fired) {
            return false;
        }
        fired = true;
        return true;
    }
}
