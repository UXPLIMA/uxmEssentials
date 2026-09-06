package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Test helper that builds a {@link ClickContext} for the apply seams a property exposes for unit testing
 * {@code TextProperty.applyInput}, {@code ListProperty.applyAdd}/{@code applyEdit}, {@code ColourProperty.applyCustom}.
 * Those seams validate and write a value without ever painting a child window, so the selector and confirm openers go
 * unconsulted; this carrier hands them harmless no-ops and a no-op reopen, letting a test drive the seam without
 * standing up a live menu engine.
 */
@NullMarked
public final class ClickContexts {

    private ClickContexts() {}

    /** A carrier context with no-op openers and reopen, for driving a property's apply seam directly in a test. */
    public static ClickContext carrier(Player player, PlayerRef viewer) {
        return new ClickContext(
                player,
                viewer,
                false,
                false,
                () -> {},
                (v, title, rows, filler, buttons) -> {},
                (v, title, onYes, onNo) -> {});
    }
}
