package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /regions [world]} list: a paginated, engine-backed panel (the shared {@link EntityListView}, so it
 * renders through the menu engine and needs no raw-inventory allow-list entry) of one icon per WorldGuard region in
 * the chosen world, priority-first. The region set, and each region's priority and member/owner counts, is read
 * off the tick thread through the {@link RegionService} (WorldGuard's region store is queried on the global region
 * thread, never a viewer's region thread), then the panel opens on the staff member's own entity thread; the icon
 * renderer reads only that snapshot.
 *
 * <p>Clicking a region hands it to the injected {@code onRegionSelected} callback. The wiring points it at the
 * Phase 2 flag editor (gated on the flags permission); the members/owners editor joins it in Phase 3. A world with
 * no regions sends the "no regions" line instead of an empty window. A fresh {@link EntityListView} is built per
 * open, so two staff inspecting different worlds never share list state.
 */
@NullMarked
public final class RegionListView {

    /** The icon each region is drawn as, and the paginated-list fallback icon. */
    private static final Material REGION_ICON = Material.PAPER;

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final RegionService service;
    private final EntityListLayout layout;
    private final BiConsumer<Player, RegionRef> onRegionSelected;

    public RegionListView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink,
            RegionService service,
            EntityListLayout layout,
            BiConsumer<Player, RegionRef> onRegionSelected) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.service = Objects.requireNonNull(service, "service");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.onRegionSelected = Objects.requireNonNull(onRegionSelected, "onRegionSelected");
    }

    /**
     * Read {@code world}'s regions off the tick thread, then open the priority-first list for {@code staff}, or,
     * when the world holds none, send {@code staff} the "no regions" line instead of an empty window.
     */
    public void open(PlayerRef staff, WorldRef world) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(world, "world");
        scheduler.onGlobal(() -> {
            List<RegionRef> regions = service.regionsIn(world);
            if (regions.isEmpty()) {
                messageSink.deliver(
                        staff,
                        messages.resolve(staff, RegionsMessageKey.REGIONS_NO_REGIONS, Map.of("world", world.name())));
                return;
            }
            List<RegionRow> rows =
                    regions.stream().map(this::toRow).sorted(byPriorityThenId()).toList();
            scheduler.onEntity(staff, () -> openResolved(staff, rows));
        });
    }

    /** Snapshot one region's priority and roster counts on the reading thread, so the icon renderer never queries. */
    private RegionRow toRow(RegionRef region) {
        return new RegionRow(
                region,
                service.priority(region),
                service.members(region).size(),
                service.owners(region).size());
    }

    /** Highest priority first; ties broken by region id so the order is stable across opens. */
    private static Comparator<RegionRow> byPriorityThenId() {
        return Comparator.comparingInt(RegionRow::priority)
                .reversed()
                .thenComparing(row -> row.region().id());
    }

    private void openResolved(PlayerRef staff, List<RegionRow> rows) {
        Player viewer = Bukkit.getPlayer(staff.uuid());
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        EntityListView.<RegionRow>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(RegionsMessageKey.REGIONS_GUI_TITLE)
                .emptyTitle(RegionsMessageKey.REGIONS_GUI_EMPTY)
                .navNames(RegionsMessageKey.REGIONS_GUI_PREV, RegionsMessageKey.REGIONS_GUI_NEXT)
                .entities(() -> rows)
                .iconRenderer(this::icon)
                .onSelect(this::onSelect)
                .build()
                .open(viewer, staff);
    }

    private ItemStack icon(PlayerRef viewer, RegionRow row) {
        Map<String, String> placeholders = Map.of(
                "id", row.region().id(),
                "priority", Integer.toString(row.priority()),
                "members", Integer.toString(row.memberCount()),
                "owners", Integer.toString(row.ownerCount()));
        return ItemBuilder.of(REGION_ICON)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, RegionsMessageKey.REGIONS_GUI_REGION, placeholders),
                        guiText.text(viewer, RegionsMessageKey.REGIONS_GUI_REGION_INFO, placeholders)))
                .build();
    }

    /** A click hands the region to the injected selection callback (the wiring opens the flag editor for it). */
    private void onSelect(Player clicker, RegionRow row) {
        onRegionSelected.accept(clicker, row.region());
    }
}
