package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /invsee} and {@code /endersee} {@code [player]}: open a live view of a subject's inventory or ender
 * chest in the viewer's own screen. A live-only effect through the {@link InventoryViewer} port (no persisted
 * flag, no domain event), then a confirmation to the viewer naming whose storage they are looking at.
 *
 * <p>There is no self/other split here: the target is always someone, and viewing your own storage via
 * {@code /invsee} is harmless, so a single {@code {player}}-keyed confirmation covers both, the default target
 * (yourself) simply yields your own name in the placeholder.
 */
public final class OpenContainer {

    private final InventoryViewer viewer;
    private final Notifier notifier;

    public OpenContainer(InventoryViewer viewer, Notifier notifier) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Open {@code subject}'s inventory in {@code looker}'s screen and confirm to {@code looker}. */
    public void openInventory(PlayerRef looker, PlayerRef subject) {
        Objects.requireNonNull(looker, "looker");
        Objects.requireNonNull(subject, "subject");
        viewer.viewInventory(looker, subject);
        notifier.send(looker, PlayerstateMessageKey.INVSEE_OPENED, Map.of("player", subject.name()));
    }

    /** Open {@code subject}'s ender chest in {@code looker}'s screen and confirm to {@code looker}. */
    public void openEnderChest(PlayerRef looker, PlayerRef subject) {
        Objects.requireNonNull(looker, "looker");
        Objects.requireNonNull(subject, "subject");
        viewer.viewEnderChest(looker, subject);
        notifier.send(looker, PlayerstateMessageKey.ENDERSEE_OPENED, Map.of("player", subject.name()));
    }
}
