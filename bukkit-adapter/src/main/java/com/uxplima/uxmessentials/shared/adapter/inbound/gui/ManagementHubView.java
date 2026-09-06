package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /uxmess gui} hub menu: a config-driven paginated grid of the {@link ManagementGuiRegistry}
 * entries the viewer is permitted, each a clickable icon that opens the owning module's management GUI.
 *
 * <p>The hub is itself a consumer of the SP0-a framework. It draws through {@link EntityListView} with
 * {@link ManagementGuiEntry} as the entity type, so geometry/materials come from
 * {@code modules/management/gui/hub.conf} and every visible string from the {@code gui.hub.*} catalog
 * block. The permitted-entry set is re-read on each open (a viewer who gains a node mid-session sees the
 * new entry next time), and a click hands control to the entry's opener. No bespoke listener is needed:
 * the menu routes through the installed uxmLib menu listener like every other framework view.
 */
@NullMarked
public final class ManagementHubView {

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Permissions permissions;
    private final ManagementGuiRegistry registry;
    private final EntityListLayout layout;

    public ManagementHubView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Permissions permissions,
            ManagementGuiRegistry registry,
            EntityListLayout layout) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** Open the hub for {@code viewer}, listing only the entries they may see, on their entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        view(viewer).open(player, viewer);
    }

    private EntityListView<ManagementGuiEntry> view(PlayerRef viewer) {
        return EntityListView.<ManagementGuiEntry>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(GuiMessageKey.HUB_TITLE)
                .navNames(GuiMessageKey.HUB_PREV, GuiMessageKey.HUB_NEXT)
                .entities(() -> registry.entriesFor(viewer, permissions))
                .iconRenderer(this::icon)
                .onSelect((player, entry) -> entry.open(player, viewer))
                .build();
    }

    private ItemStack icon(PlayerRef viewer, ManagementGuiEntry entry) {
        Map<String, String> placeholders = Map.of("module", entry.id());
        // The title is the module's own panel name, not its id: the id is a wiring key and reads like one, and it
        // stays where it belongs, on the information row of the tooltip.
        return ItemBuilder.of(entry.icon())
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, entry.label()),
                        guiText.text(viewer, GuiMessageKey.HUB_ENTRY_LORE, placeholders)))
                .build();
    }
}
