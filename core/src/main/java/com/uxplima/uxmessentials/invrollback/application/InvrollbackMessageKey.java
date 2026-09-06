package com.uxplima.uxmessentials.invrollback.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The invrollback context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code INVROLLBACK_RESTORED} ↔ {@code invrollback.restored}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context.
 *
 * <p>Phase 1's capture path is silent (a snapshot is taken in the background with no player-facing feedback) so
 * these are the seed for the Phase 2 {@code /invrestore} staff surface (the snapshot listing, the restore
 * confirmation, and the not-found refusals). Per the i18n contract the keys ship now even though the command that
 * resolves them lands later, so the catalog stays whole and the locale-parity guard sees the full {@code en} key
 * set.
 */
public enum InvrollbackMessageKey implements MessageKey {

    // Refusals shown to the staff member running the (Phase 2) /invrestore command.
    INVROLLBACK_NO_SNAPSHOTS("invrollback.no-snapshots"),
    INVROLLBACK_PLAYER_NOT_FOUND("invrollback.player-not-found"),
    INVROLLBACK_SNAPSHOT_NOT_FOUND("invrollback.snapshot-not-found"),

    // Confirmation that a target's inventory was restored from a chosen snapshot.
    INVROLLBACK_RESTORED("invrollback.restored"),

    // The export-to-shulkers action: the confirmation, and the refusal when the snapshot holds nothing to export.
    INVROLLBACK_EXPORTED("invrollback.exported"),
    INVROLLBACK_NOTHING_TO_EXPORT("invrollback.nothing-to-export"),

    // The teleport-to-scene action: the confirmation, the refusal for a pre-location snapshot, and the refusal
    // when the recorded world is no longer loaded.
    INVROLLBACK_TELEPORTED("invrollback.teleported"),
    INVROLLBACK_NO_LOCATION("invrollback.no-location"),
    INVROLLBACK_WORLD_UNAVAILABLE("invrollback.world-unavailable"),

    // The restore GUI: the panel title, a per-snapshot list line (cause + timestamp), the info block (cause, time,
    // counts), the location line, and the restore action.
    INVROLLBACK_GUI_TITLE("invrollback.gui.title"),
    INVROLLBACK_GUI_SNAPSHOT("invrollback.gui.snapshot"),
    INVROLLBACK_GUI_INFO("invrollback.gui.info"),
    INVROLLBACK_GUI_INFO_NAME("invrollback.gui.info-name"),
    INVROLLBACK_GUI_LOCATION("invrollback.gui.location"),
    INVROLLBACK_GUI_VIEW("invrollback.gui.view"),
    INVROLLBACK_GUI_RESTORE("invrollback.gui.restore"),

    // The snapshot preview window: its title (cause + timestamp of the previewed snapshot), the restore/teleport/
    // export button labels and hints, the exported-shulker item name and lore, plus the list's page buttons.
    INVROLLBACK_GUI_PREVIEW_TITLE("invrollback.gui.preview-title"),
    INVROLLBACK_GUI_RESTORE_NAME("invrollback.gui.restore-name"),
    INVROLLBACK_GUI_TELEPORT_NAME("invrollback.gui.teleport-name"),
    INVROLLBACK_GUI_TELEPORT("invrollback.gui.teleport"),
    INVROLLBACK_GUI_EXPORT_NAME("invrollback.gui.export-name"),
    INVROLLBACK_GUI_EXPORT("invrollback.gui.export"),
    INVROLLBACK_GUI_SHULKER_NAME("invrollback.gui.shulker-name"),
    INVROLLBACK_GUI_SHULKER_LORE("invrollback.gui.shulker-lore"),
    INVROLLBACK_GUI_PREV("invrollback.gui.prev"),
    INVROLLBACK_GUI_NEXT("invrollback.gui.next");

    private final String key;

    InvrollbackMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
