package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec.Summary;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Opens a chosen snapshot's contents as a read-only preview window with a control row: a details panel, a teleport
 * button, a restore button, and an export-to-shulkers button. The window is a menu spec
 * ({@link SnapshotPreviewWindow}), so an operator re-skins its chrome and moves its buttons; this class owns the
 * wording, resolved from the message catalog in the viewer's locale, and what each button does, a hand-off to the
 * {@link SnapshotRestorer}, {@link SnapshotTeleporter}, or {@link SnapshotExporter}.
 *
 * <p>The snapshot's own items sit in a read-only content region, so no click can take one out. No edit is ever
 * reconciled back either: the preview shows a stored snapshot, and only the explicit controls mutate anything, so
 * there is nothing to track or flush on close.
 *
 * <p>The open is scheduled on the staff member's own entity thread, where touching their live screen is legal.
 */
@NullMarked
public final class SnapshotPreviewView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final Clock clock;
    private final SnapshotPreviewWindow window;
    private final SnapshotRestorer restorer;
    private final SnapshotTeleporter teleporter;
    private final SnapshotExporter exporter;

    public SnapshotPreviewView(
            Messages messages,
            Scheduler scheduler,
            Clock clock,
            SnapshotPreviewWindow window,
            SnapshotRestorer restorer,
            SnapshotTeleporter teleporter,
            SnapshotExporter exporter) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.window = Objects.requireNonNull(window, "window");
        this.restorer = Objects.requireNonNull(restorer, "restorer");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    /** Register the window's spec and its bindings; the wiring calls this once, before the first preview. */
    public void register(MenuBindings bindings) {
        window.register(bindings, this);
    }

    /** Open a read-only preview of {@code snapshot} (owned by {@code target}) for {@code staff}. */
    public void open(PlayerRef staff, PlayerRef target, Snapshot snapshot) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        scheduler.onEntity(staff, () -> window.open(new SnapshotPreview(staff, target, snapshot)));
    }

    /** The window's title: who and when, resolved in {@code viewer}'s locale. */
    String previewTitle(SnapshotPreview preview, PlayerRef viewer) {
        return messages.resolve(viewer, InvrollbackMessageKey.INVROLLBACK_GUI_PREVIEW_TITLE, base(preview));
    }

    /**
     * The details panel's lore: the snapshot summary, and the recorded location when it has one. The two lines are
     * joined with a newline, which the engine splits back into separate lore lines.
     */
    String infoLore(SnapshotPreview preview, PlayerRef viewer) {
        Summary summary = summarize(preview);
        List<String> lines = new ArrayList<>();
        lines.add(messages.resolve(viewer, InvrollbackMessageKey.INVROLLBACK_GUI_INFO, base(preview)));
        summary.location()
                .ifPresent(position -> lines.add(messages.resolve(
                        viewer, InvrollbackMessageKey.INVROLLBACK_GUI_LOCATION, SnapshotDisplay.location(position))));
        return String.join("\n", lines);
    }

    /** Route a restore-button click: close the preview and hand the restore to the {@link SnapshotRestorer}. */
    void onRestoreClick(SnapshotPreview preview, Player viewer) {
        viewer.closeInventory();
        restorer.restore(BukkitRefs.toRef(viewer), preview.target(), preview.snapshotId());
    }

    /** Route a teleport-button click: close the preview and hand the hop to the {@link SnapshotTeleporter}. */
    void onTeleportClick(SnapshotPreview preview, Player viewer) {
        viewer.closeInventory();
        teleporter.teleport(BukkitRefs.toRef(viewer), preview.target(), preview.snapshot());
    }

    /** Route an export-button click: close the preview and hand the packaging to the {@link SnapshotExporter}. */
    void onExportClick(SnapshotPreview preview, Player viewer) {
        viewer.closeInventory();
        exporter.export(BukkitRefs.toRef(viewer), preview.target(), preview.snapshot());
    }

    private Map<String, String> base(SnapshotPreview preview) {
        return SnapshotDisplay.base(preview.target(), preview.snapshot(), summarize(preview), clock.instant());
    }

    private static Summary summarize(SnapshotPreview preview) {
        return InventorySnapshotCodec.summarize(preview.snapshot().contents());
    }
}
