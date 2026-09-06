package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One module's contribution to the {@code /uxmess gui} management hub: the label and icon the hub draws
 * for it, the permission a viewer needs to see and use it, and the opener that launches the module's own
 * management GUI.
 *
 * <p>A module registers exactly one of these in its own wiring (through {@link ManagementGuiRegistry}),
 * closing the {@code opener} over its already-built list view. The hub holds no module logic: it only
 * draws the registered entries the viewer is permitted, and on a click hands control to {@code opener}.
 * The {@code label} (a {@link MessageKey}) keeps the hub's text in the catalog and the {@code icon}
 * material is read from the hub conf-supplied default unless the module overrides it; nothing is inlined.
 *
 * @param id a stable identifier (the module id), for ordering and deduplication
 * @param label the catalog key for the entry's display name
 * @param icon the material the hub draws for this entry
 * @param permission the node a viewer must hold to see and open this entry
 * @param opener launches the module's management GUI for the clicking player
 */
@NullMarked
public record ManagementGuiEntry(
        String id, MessageKey label, Material icon, String permission, BiConsumer<Player, PlayerRef> opener) {

    public ManagementGuiEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(opener, "opener");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must be non-blank");
        }
        if (permission.isBlank()) {
            throw new IllegalArgumentException("permission must be non-blank");
        }
    }

    /** Open this module's management GUI for {@code player}. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        opener.accept(player, viewer);
    }
}
