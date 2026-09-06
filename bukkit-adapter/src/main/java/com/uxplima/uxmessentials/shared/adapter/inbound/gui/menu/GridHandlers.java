package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The click callbacks a caller hands {@link Menus#openGrid}, the behaviour half of a grid open, kept separate from
 * the {@link GridSpec} layout half so the same visual canvas can be driven by different editing policy. Every grid
 * carries a content-slot handler for its editor gestures; a grid may additionally carry a {@link GridCaptureHandler},
 * which makes it "capture-enabled": the operator can then drag or place one of their own items straight onto a content
 * cell (or shift-click it out of their inventory) and the engine stamps a copy of its definition into the slot instead
 * of blanket-cancelling the click. A grid with a {@code null} capture handler stays blanket-cancelled like every other
 * menu, so the control-bar buttons and the prev/next pagination on the {@link GridSpec} are unaffected either way.
 */
@NullMarked
public record GridHandlers(
        GridClickHandler onSlot, @Nullable GridCaptureHandler onCapture) {

    public GridHandlers {
        Objects.requireNonNull(onSlot, "onSlot");
    }

    /** A grid with editor gestures only, no capture, the shape every non-editor caller uses. */
    public GridHandlers(GridClickHandler onSlot) {
        this(onSlot, null);
    }
}
