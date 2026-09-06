package com.uxplima.uxmessentials.presence.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /list} browse menu: a config-driven, paginated grid of the online players' heads, drawn through the
 * shared {@link EntityListView}. It is the visual twin of the {@code /list} chat line, same vanish-aware roster
 * (a player only sees those their {@code canSee} graph allows), here one head per player with the same world and
 * AFK/vanish status the chat path already knows. The view is read-only: a click is a no-op, there is no pick
 * action.
 *
 * <p>The view holds no enumeration of its own. The {@code ListCommand} already snapshots the visible roster on
 * the global region thread (Folia forbids iterating the online set off it) and hands the resulting
 * {@link Entry} list to {@link #open}; this class only renders it. The snapshot is held in an
 * {@link AtomicReference} the {@link EntityListView}'s supplier reads when it builds the page, so the grid never
 * touches Bukkit's online set.
 */
@NullMarked
public final class OnlinePlayerListView {

    private final GuiText guiText;
    private final AtomicReference<List<Entry>> snapshot = new AtomicReference<>(List.of());
    private final EntityListView<Entry> view;

    public OnlinePlayerListView(Menus menus, GuiText guiText, Scheduler scheduler, EntityListLayout layout) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(layout, "layout");
        this.view = EntityListView.<Entry>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(PresenceMessageKey.LIST_GUI_TITLE)
                .emptyTitle(PresenceMessageKey.LIST_GUI_EMPTY_TITLE)
                .navNames(PresenceMessageKey.LIST_GUI_PREV, PresenceMessageKey.LIST_GUI_NEXT)
                .entities(snapshot::get)
                // Read-only: clicking a head does nothing (no pick action, per item 32).
                .onSelect((player, entry) -> {})
                .iconRenderer(this::head)
                .build();
    }

    /**
     * Open the roster menu for {@code viewer} over the already-snapshotted {@code roster}. The caller enumerated
     * the visible online set on the global region thread; this only renders it. An empty roster opens the same engine
     * list under its empty-state title. The engine draws just the filler and nav, an empty-state panel rather than a
     * head grid, so even the empty case is one holder-backed engine window, never a bespoke menu.
     */
    public void open(Player player, PlayerRef viewer, List<Entry> roster) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(roster, "roster");
        snapshot.set(List.copyOf(roster));
        view.open(player, viewer);
    }

    private ItemStack head(PlayerRef viewer, Entry entry) {
        Map<String, String> placeholders =
                Map.of("player", entry.name(), "world", entry.world(), "status", entry.status());
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, PresenceMessageKey.LIST_GUI_ENTRY_NAME, placeholders),
                        guiText.text(viewer, PresenceMessageKey.LIST_GUI_ENTRY_LORE, placeholders)))
                .skull(SkullData.ofUuid(entry.uuid()))
                .build();
    }

    /**
     * One online player as the menu sees them: the account uuid (for the head skin), the visible name, the world
     * they are in, and a short status string ({@code AFK} / {@code Vanished} / {@code Online}). The
     * {@code ListCommand} resolves these on the global thread when it snapshots the roster.
     *
     * @param uuid the player's account uuid, used to skin the head
     * @param name the player's visible name
     * @param world the world the player is currently in
     * @param status a short presence status word
     */
    public record Entry(UUID uuid, String name, String world, String status) {

        public Entry {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(status, "status");
        }
    }
}
