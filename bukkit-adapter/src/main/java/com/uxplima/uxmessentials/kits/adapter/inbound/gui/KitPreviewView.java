package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.MenuTitles;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Opens {@code /kit show} as a read-only managed menu: the kit's stacks laid out in their definition-order slots
 * ({@link KitGuiLayout}), with the trailing slots padded by a gray-glass filler. The menu is sized to the
 * smallest whole number of rows that holds the kit (capped at a 54-slot double chest). It is read-only, every
 * click, drag, and hotbar swap is cancelled by {@link KitPreviewListener}, recognised by the menu's
 * {@link KitPreviewHolder}, so a player can inspect a kit's contents in their real slots without taking
 * anything, and the menu holds no item the player could pull out.
 *
 * <p>{@link #open} touches the live player (it builds and opens an inventory in their screen), so the caller
 * schedules it on the viewer's entity thread through the kernel {@link Scheduler}. The title is a
 * {@link com.uxplima.uxmessentials.shared.application.message.MessageKey} rendered in the viewer's locale and
 * parsed into a {@code Component}, never an inline literal.
 */
@NullMarked
public final class KitPreviewView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final GuiLayout layout;

    public KitPreviewView(Messages messages, Scheduler scheduler, GuiLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** Open the read-only preview of {@code kit} for {@code player}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, KitDefinition kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kit, "kit");
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                openResolved(live, viewer, kit);
            }
        });
    }

    private void openResolved(Player player, PlayerRef viewer, KitDefinition kit) {
        int minRows = KitGuiLayout.rowsFor(kit.items().size());
        int rows = Math.min(KitGuiLayout.MAX_ROWS, Math.max(layout.rows(), minRows));
        KitPreviewHolder holder = new KitPreviewHolder();
        Inventory menu = Bukkit.createInventory(holder, rows * KitGuiLayout.SLOTS_PER_ROW, title(viewer, kit));
        holder.attach(menu);
        KitGuiLayout.seed(menu, kit.items(), layout.fallbackIcon());
        player.openInventory(menu);
    }

    private Component title(PlayerRef viewer, KitDefinition kit) {
        String rendered = messages.resolve(
                viewer,
                KitsMessageKey.KIT_PREVIEW_GUI_TITLE,
                Map.of("kit", kit.id().value()));
        return MenuTitles.centre(StyledText.render(rendered));
    }
}
