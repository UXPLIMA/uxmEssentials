package com.uxplima.uxmessentials.shared.domain.action;

/**
 * Which kind of click fires a {@link ClickAction}. A player's interaction with a click target arrives as either an
 * attack (the left mouse button) or an interact (the right button), and an action declares which it responds to:
 * {@link #LEFT_CLICK} only on an attack, {@link #RIGHT_CLICK} only on an interact, and {@link #ANY} on both. The
 * adapter passes the raw {@code attack} flag and {@link #matches(boolean)} decides, keeping the left/right
 * meaning of the flag in the domain rather than scattered across the listener.
 */
public enum ClickTrigger {

    /** Fires only when the player attacks (left-clicks) the target. */
    LEFT_CLICK,

    /** Fires only when the player interacts with (right-clicks) the target. */
    RIGHT_CLICK,

    /** Fires on either an attack or an interact. */
    ANY;

    /** Whether this trigger fires for a click whose {@code attack} flag is as given (true = left, false = right). */
    public boolean matches(boolean attack) {
        return switch (this) {
            case LEFT_CLICK -> attack;
            case RIGHT_CLICK -> !attack;
            case ANY -> true;
        };
    }
}
