package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One open managed mirror window: who is looking, whose container they are looking at, whether they may edit it,
 * which kind of container it is, the snapshot the window was seeded from, and where its final contents go. It is
 * the subject the menu carries, so the content provider reads the window's state and its destination from here
 * rather than having to know which of the three views opened it.
 *
 * <p>Identity is the object itself, not its fields: two staff may have the same target open at once and each window
 * must be claimed and written back on its own, which is what lets a view track one entry per open window.
 */
@NullMarked
final class MirrorHolder {

    /** Where a mirror window's final contents go. The view that opened the window supplies this. */
    @FunctionalInterface
    interface WriteBack {
        /**
         * Reconcile {@code contents}, the window's region in its declared slot order, onto the holder's target.
         * Called at most once per window: the implementation claims the window first, so whichever of the close or
         * the module-stop drain reaches it first is the only one that writes.
         */
        void accept(MirrorHolder holder, @Nullable ItemStack[] contents);
    }

    private final PlayerRef viewer;
    private final PlayerRef target;
    private final boolean editable;
    private final MirrorKind kind;
    private final @Nullable ItemStack[] snapshot;
    private final WriteBack writeBack;

    MirrorHolder(
            PlayerRef viewer,
            PlayerRef target,
            boolean editable,
            MirrorKind kind,
            @Nullable ItemStack[] snapshot,
            WriteBack writeBack) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.target = Objects.requireNonNull(target, "target");
        this.editable = editable;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot").clone();
        this.writeBack = Objects.requireNonNull(writeBack, "writeBack");
    }

    /** The staff member the window is open for. */
    PlayerRef viewer() {
        return viewer;
    }

    /** The player whose container this window mirrors and writes back to. */
    PlayerRef target() {
        return target;
    }

    /** Whether the viewer holds the modify node; a view-only window refuses every movement in its region. */
    boolean editable() {
        return editable;
    }

    /** Which container is mirrored. */
    MirrorKind kind() {
        return kind;
    }

    /** The items the window was seeded with, as a fresh array each call so no caller can mutate the snapshot. */
    @Nullable ItemStack[] snapshot() {
        return snapshot.clone();
    }

    /** Hand this window's final contents to the view that opened it. */
    void writeBack(@Nullable ItemStack[] contents) {
        writeBack.accept(this, contents);
    }
}
