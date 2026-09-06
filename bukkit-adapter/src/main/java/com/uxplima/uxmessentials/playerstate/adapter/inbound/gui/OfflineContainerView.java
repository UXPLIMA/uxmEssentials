package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflineInventory;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens {@code /invsee} and {@code /endersee} against an <em>offline</em> target. The target's stored items are read
 * from disk through {@link OfflinePlayerStorage} on the async lane, then the same dupe-safe managed menu the online
 * {@link InvseeView} and {@link EnderseeView} use is opened on the viewer's entity thread and seeded from that
 * snapshot. The viewer edits the copy; on close the edits are reconciled back.
 *
 * <p>The write-back resolves the login race: if the target is still offline, the edit is written to their
 * {@code playerdata} file on the async lane; if they have logged in while the menu was open, it is applied to their
 * live inventory on their own entity thread instead (a disk write would be lost under their live session). Offline
 * {@code /invsee} respects the {@code uxmessentials.invsee.modify} gate just like the online view; offline
 * {@code /endersee} is editable, mirroring the live ender-chest open. Every open window is tracked so a single
 * write-back claims it, whichever of the close or {@link #flushAll} (on module stop) reaches it first.
 */
@NullMarked
public final class OfflineContainerView {

    private static final String MODIFY_PERMISSION = "uxmessentials.invsee.modify";

    private final Scheduler scheduler;
    private final OfflinePlayerStorage storage;
    private final MirrorWindow window;
    private final Set<MirrorHolder> open = ConcurrentHashMap.newKeySet();

    public OfflineContainerView(Scheduler scheduler, OfflinePlayerStorage storage, MirrorWindow window) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.window = Objects.requireNonNull(window, "window");
    }

    /** Open {@code subject}'s stored inventory for {@code viewer}. */
    public void openInventory(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        loadThen(
                viewer,
                subject,
                (looker, snapshot) -> openResolved(
                        viewer,
                        subject,
                        looker.hasPermission(MODIFY_PERMISSION),
                        MirrorKind.INVENTORY,
                        snapshot.slots()));
    }

    /** Open {@code subject}'s stored ender chest for {@code viewer}. */
    public void openEnderChest(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        loadThen(
                viewer,
                subject,
                (looker, snapshot) -> openResolved(viewer, subject, true, MirrorKind.ENDER, snapshot.ender()));
    }

    /** Write back and forget every still-open offline menu; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (MirrorHolder holder : Set.copyOf(open)) {
            window.drain(holder);
        }
    }

    /** The number of offline storage menus currently open. */
    public int openCount() {
        return open.size();
    }

    private void loadThen(PlayerRef viewer, PlayerRef subject, BiConsumer<Player, OfflineInventory> opener) {
        scheduler.async(() -> storage.load(subject.uuid())
                .ifPresent(snapshot -> scheduler.onEntity(viewer, () -> {
                    Player looker = Bukkit.getPlayer(viewer.uuid());
                    if (looker != null && looker.isOnline()) {
                        opener.accept(looker, snapshot);
                    }
                })));
    }

    private void openResolved(
            PlayerRef viewer, PlayerRef subject, boolean editable, MirrorKind kind, @Nullable ItemStack[] snapshot) {
        MirrorHolder holder = new MirrorHolder(viewer, subject, editable, kind, snapshot, this::reconcile);
        open.add(holder);
        window.open(holder);
    }

    /**
     * Claim the window and reconcile its final contents. A view-only window changed nothing, so writing it back
     * would be a needless disk write over a target another staff member may be editing; it is dropped instead.
     */
    private void reconcile(MirrorHolder holder, @Nullable ItemStack[] contents) {
        if (!open.remove(holder) || !holder.editable()) {
            return;
        }
        PlayerRef subject = holder.target();
        Player live = Bukkit.getPlayer(subject.uuid());
        if (live != null && live.isOnline()) {
            scheduler.onEntity(subject, () -> {
                Player target = Bukkit.getPlayer(subject.uuid());
                if (target != null && target.isOnline()) {
                    applyLive(holder.kind(), contents, target);
                }
            });
        } else if (holder.kind() == MirrorKind.INVENTORY) {
            scheduler.async(() -> storage.saveInventory(subject.uuid(), contents));
        } else {
            scheduler.async(() -> storage.saveEnderChest(subject.uuid(), contents));
        }
    }

    private static void applyLive(MirrorKind kind, @Nullable ItemStack[] contents, Player target) {
        if (kind == MirrorKind.INVENTORY) {
            InvseeLayout.applySlots(contents, target);
        } else {
            EnderLayout.applySlots(contents, target);
        }
    }
}
