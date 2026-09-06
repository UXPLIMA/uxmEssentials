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
 * Opens {@code /invsee} as a managed menu that mirrors a target's full inventory. Main slots, armour, and offhand
 * (see {@link InvseeLayout}), and reconciles the viewer's edits back onto the target when the menu closes. The
 * viewer edits this private copy, never the target's live {@link org.bukkit.inventory.PlayerInventory} object, which
 * is what closes the classic raw-inventory dupe window: a shift-click or cursor move shuffles items inside the copy
 * only, and {@link InvseeLayout#applySlots} applies the final state in one pass on close.
 *
 * <p>The window itself is a menu spec ({@link MirrorWindow}), so an operator re-skins its chrome without any of the
 * item-safety rules moving into a config file.
 *
 * <p>The open runs on the viewer's entity thread (the menu lives in their screen); the write-back runs on the
 * target's entity thread (it mutates the target's entity), each through the kernel {@link Scheduler}. Every open
 * window is tracked so a single write-back claims it, whichever of the close or {@link #flushAll} (on module stop)
 * reaches it first, and a still-open window is never written back twice. A target who logged off before the close
 * drops their write-back silently; the menu copy is simply discarded.
 */
@NullMarked
public final class InvseeView {

    private static final String MODIFY_PERMISSION = "uxmessentials.invsee.modify";

    private final Scheduler scheduler;
    private final MirrorWindow window;
    private final Set<MirrorHolder> open = ConcurrentHashMap.newKeySet();

    public InvseeView(Scheduler scheduler, MirrorWindow window) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.window = Objects.requireNonNull(window, "window");
    }

    /**
     * Open {@code subject}'s inventory for {@code viewer}. The target's items are snapshotted on the target's own
     * entity thread. On Folia the target's live inventory is owned by that region thread, so reading it from the
     * viewer's thread is the asymmetric unsafe half this fix removes, and the menu is then opened on the viewer's
     * entity thread from that snapshot. The open is skipped when either player has gone offline. The viewer's edit
     * right is the {@code uxmessentials.invsee.modify} node read off the live viewer there, without it the menu
     * opens view-only (every movement refused).
     */
    public void open(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        scheduler.onEntity(subject, () -> {
            Player target = Bukkit.getPlayer(subject.uuid());
            if (target == null || !target.isOnline()) {
                return;
            }
            @Nullable ItemStack[] snapshot = InvseeLayout.fromPlayer(target);
            scheduler.onEntity(viewer, () -> {
                Player looker = Bukkit.getPlayer(viewer.uuid());
                if (looker != null && looker.isOnline()) {
                    openResolved(viewer, subject, snapshot, looker.hasPermission(MODIFY_PERMISSION));
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

    /** The number of invsee menus currently open (the {@code open-guis=N} a doctor line could report). */
    public int openCount() {
        return open.size();
    }

    private void openResolved(PlayerRef viewer, PlayerRef subject, @Nullable ItemStack[] snapshot, boolean editable) {
        MirrorHolder holder =
                new MirrorHolder(viewer, subject, editable, MirrorKind.INVENTORY, snapshot, this::persist);
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
                InvseeLayout.applySlots(contents, target);
            }
        });
    }
}
