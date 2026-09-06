package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

/**
 * The click gestures a spec can bind behaviour to. {@link #ANY} is a catch-all that fires alongside whichever
 * specific kind the player used, so an author can attach a shared sound or condition once instead of repeating
 * it under every gesture.
 *
 * <p>{@link #DROP} (the drop key over a slot), {@link #CONTROL_DROP} (drop-stack), and {@link #DOUBLE_CLICK} are the
 * three gestures beyond the mouse buttons. A caveat worth knowing when binding {@code DOUBLE_CLICK}: the client fires
 * a preceding {@code LEFT} click before the double, so a spec that binds both {@code left} and {@code double_click} on
 * one item sees both run on a double, which is how vanilla reports the gesture, not a fault in the engine.
 */
public enum ClickKind {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    DROP,
    CONTROL_DROP,
    DOUBLE_CLICK,
    ANY
}
