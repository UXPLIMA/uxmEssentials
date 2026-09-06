package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags a {@code /kit editor} window, so {@link KitEditorListener} can recognise a
 * close as belonging to one of these editable views (and never to a vanilla container the editor happens to have
 * open) and read the kit being edited and who is editing it. The holder carries the kit's definition as it stood
 * when the window opened, so the close handler can rebuild the replacement from the window's items while
 * preserving the kit's cooldown, one-time flag, permission flag, and cost: only the items are edited here.
 *
 * <p>The holder is created first and the menu is built against it; {@link #attach} then stores the built
 * inventory so {@link #getInventory()} can answer it, the way Bukkit's holder contract expects.
 */
@NullMarked
final class KitEditorHolder implements InventoryHolder {

    private final PlayerRef editor;
    private final KitDefinition kit;
    private @Nullable Inventory inventory;

    KitEditorHolder(PlayerRef editor, KitDefinition kit) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.kit = Objects.requireNonNull(kit, "kit");
    }

    /** The staff member editing the kit; the save confirmation and persist are attributed to them. */
    PlayerRef editor() {
        return editor;
    }

    /** The kit as it stood when the window opened; the close handler overwrites only its items. */
    KitDefinition kit() {
        return kit;
    }

    /** Store the built menu so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("kit editor inventory not attached yet");
        }
        return built;
    }
}
