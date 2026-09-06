package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens an online {@code /endersee} as a managed menu that mirrors a target's ender chest (see {@link EnderLayout})
 * and reconciles the viewer's edits back onto the target when the menu closes. The viewer edits this private copy,
 * never the target's live {@link Player#getEnderChest()} object: handing the viewer the live container would let
 * every subsequent click read and write that foreign container from the viewer's region thread, which on Folia is
 * the cross-region hazard this view removes. A relocation inside the copy shuffles items in the copy only, and
 * {@link EnderLayout#applySlots} applies the final state in one pass on close.
 *
 * <p>The open runs on the viewer's entity thread (the menu lives in their screen); the target's contents are
 * snapshotted first on the target's own entity thread (on Folia the live ender chest is owned by that region
 * thread). The write-back runs on the target's entity thread (it mutates the target's entity), each through the
 * kernel {@link Scheduler}. Every open window is tracked so a single write-back claims it, whichever of the close
 * or {@link #flushAll} (on module stop) reaches it first, and a still-open window is never written back twice. A
 * target who logged off before the close drops their write-back silently; the menu copy is discarded.
 *
 * <p>This is the ender-chest mirror of {@link InvseeView}; online {@code /endersee} is always editable, matching the
 * offline {@code /endersee} path.
 */
@NullMarked
public final class EnderseeView {

    private final Scheduler scheduler;
    private final MirrorWindow window;
    private final Set<MirrorHolder> open = ConcurrentHashMap.newKeySet();

    public EnderseeView(Scheduler scheduler, MirrorWindow window) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.window = Objects.requireNonNull(window, "window");
    }

    /**
     * Open {@code subject}'s ender chest for {@code viewer}. The target's contents are snapshotted on the target's
     * own entity thread. On Folia the live ender chest is owned by that region thread, so reading it from the
     * viewer's thread is the asymmetric unsafe half this fix removes, and the menu is then opened on the viewer's
     * entity thread from that snapshot. The open is skipped when either player has gone offline.
     */
    public void open(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        scheduler.onEntity(subject, () -> {
            Player target = Bukkit.getPlayer(subject.uuid());
            if (target == null || !target.isOnline()) {
                return;
            }
            @Nullable ItemStack[] snapshot = EnderLayout.fromPlayer(target);
            scheduler.onEntity(viewer, () -> {
                Player looker = Bukkit.getPlayer(viewer.uuid());
                if (looker != null && looker.isOnline()) {
                    openResolved(viewer, subject, snapshot);
                }
            });
        });
    }

    /** Write back and forget every still-open menu; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (MirrorHolder holder : Set.copyOf(open)) {
            window.drain(holder);
        }
    }

    /** The number of endersee menus currently open. */
    public int openCount() {
        return open.size();
    }

    private void openResolved(PlayerRef viewer, PlayerRef subject, @Nullable ItemStack[] snapshot) {
        MirrorHolder holder = new MirrorHolder(viewer, subject, true, MirrorKind.ENDER, snapshot, this::persist);
        open.add(holder);
        window.open(holder);
    }

    /** Claim the window and reconcile its final contents onto the target, on the target's own entity thread. */
    private void persist(MirrorHolder holder, @Nullable ItemStack[] contents) {
        if (!open.remove(holder)) {
            return;
        }
        PlayerRef subject = holder.target();
        scheduler.onEntity(subject, () -> {
            Player target = Bukkit.getPlayer(subject.uuid());
            if (target != null && target.isOnline()) {
                EnderLayout.applySlots(contents, target);
            }
        });
    }
}
