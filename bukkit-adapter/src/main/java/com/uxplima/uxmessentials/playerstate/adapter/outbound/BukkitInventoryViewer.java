package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.EnderseeView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.OfflineContainerView;
import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link InventoryViewer} implementation for {@code /invsee} and {@code /endersee}, routing by whether the
 * subject is online. When online, {@code /invsee} goes through the managed {@link InvseeView} and {@code /endersee}
 * through the managed {@link EnderseeView}: each opens a private copy the viewer edits and reconciles back on close,
 * never the live {@link org.bukkit.inventory.PlayerInventory} / {@link Player#getEnderChest()} object, handing the
 * viewer the live container would have every later click read and write that foreign container from the viewer's
 * region thread, the cross-region hazard (and the classic dupe vector) both managed views remove. When the subject
 * is offline, both route to the {@link OfflineContainerView}, which reads the target's stored items from disk into
 * the same kind of managed menu and writes the edits back to the {@code playerdata} file on close.
 *
 * <p>Each managed view snapshots the target on the target's own entity thread, builds and opens the menu on the
 * viewer's entity thread, and writes back on the target's entity thread, all through the injected scheduler; the
 * offline path schedules its own disk read and menu open. The routing decision reads the subject's current online
 * state on the calling (command) thread.
 */
@NullMarked
public final class BukkitInventoryViewer implements InventoryViewer {

    private final InvseeView invseeView;
    private final EnderseeView enderseeView;
    private final OfflineContainerView offlineView;

    public BukkitInventoryViewer(InvseeView invseeView, EnderseeView enderseeView, OfflineContainerView offlineView) {
        this.invseeView = Objects.requireNonNull(invseeView, "invseeView");
        this.enderseeView = Objects.requireNonNull(enderseeView, "enderseeView");
        this.offlineView = Objects.requireNonNull(offlineView, "offlineView");
    }

    @Override
    public void viewInventory(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        if (isOnline(subject)) {
            invseeView.open(viewer, subject);
        } else {
            offlineView.openInventory(viewer, subject);
        }
    }

    @Override
    public void viewEnderChest(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        if (isOnline(subject)) {
            enderseeView.open(viewer, subject);
        } else {
            offlineView.openEnderChest(viewer, subject);
        }
    }

    private static boolean isOnline(PlayerRef subject) {
        Player target = Bukkit.getPlayer(subject.uuid());
        return target != null && target.isOnline();
    }
}
