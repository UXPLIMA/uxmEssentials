package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the read-only {@code /warp} browse menu with the menu engine and opens it. The same view the old
 * {@code WarpMenuView} drew, now rendered off a spec: one tile per warp the viewer may use, paged through the
 * content slots, with previous/next buttons on the bottom row. When categories are defined the tiles mix
 * drill-in category tiles (sorted by their slot) with the warps at the current tree level, exactly as the old
 * view did; clicking a category drills in, the back button steps up to the parent, and clicking a warp warps
 * the viewer through the same {@link UseWarp} use case the {@code /warp} command drives. With no categories the
 * legacy flat list is the root level with no sub-categories: one spec serves both modes.
 *
 * <p>The level's tiles and their full lore strings are resolved up front on the viewer's entity thread (where
 * {@link #open} is called from) and handed to the engine as the menu subject, mirroring {@link WarpManagerMenu}:
 * the warp and category sets are warm in-memory reads and the catalog lookups go through the {@link Messages}
 * port in the viewer's locale, so the {@code warps:browse} list source only reads that subject and the engine
 * never touches a port off-thread. Drilling re-snapshots at the child level under the same spec; the
 * {@code warps:browse-back} action re-snapshots at the parent. A warp click runs the visit on the viewer's
 * entity thread (the hop moves the live player); {@link UseWarp} gates access, charges any cost, and delegates
 * the hop to the teleport context itself.
 */
@NullMarked
public final class WarpBrowseMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "warp-browse";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/warps/gui/warp-browse.conf";

    /** The single material every warp tile uses, since warps carry no item: matches the old view's fallback. */
    private static final Material WARP_FALLBACK_ICON = Material.ENDER_PEARL;

    /** The kind a browse tile stands for, so one list source can mix drill-in categories and warp tiles. */
    enum Kind {
        CATEGORY,
        WARP
    }

    private final Menus menus;
    private final Scheduler scheduler;
    private final UseWarp useWarp;
    private final Messages messages;
    private final WarpCategoryRepository categoryRepository;

    public WarpBrowseMenu(
            Menus menus,
            Scheduler scheduler,
            UseWarp useWarp,
            Messages messages,
            WarpCategoryRepository categoryRepository) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.useWarp = Objects.requireNonNull(useWarp, "useWarp");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
    }

    /** Register the per-tile list source, the kind-branching placeholders, the click/back actions, and the spec. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("warps:browse", ctx -> ctx.subject(BrowseLevel.class).entries());
        bindings.placeholder("warp_browse_icon", ctx -> rowOf(ctx).icon());
        bindings.placeholder("warp_browse_name", ctx -> rowOf(ctx).name());
        bindings.placeholder("warp_browse_lore", ctx -> rowOf(ctx).lore());
        bindings.action("warps:browse-click", this::click);
        bindings.action("warps:browse-back", this::back);
        // The back button shows only below the root, exactly as the old view only drew it when a category was open.
        bindings.condition(
                "warps:browse-has-parent",
                (ctx, args) -> ctx.subject(BrowseLevel.class).categoryId().isPresent());
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the browse menu listing {@code warps} for {@code viewer} at the root level, scheduled on the viewer's
     * entity thread. The level's tiles and lore are resolved there off the warm warp/category sets and the catalog,
     * and handed to the engine as the subject, so the engine renders without a port read of its own.
     */
    public void open(Player player, PlayerRef viewer, List<Warp> warps) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warps, "warps");
        List<Warp> snapshot = List.copyOf(warps);
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, level(viewer, snapshot, Optional.empty())));
    }

    /** The tile the slot being rendered or clicked stands for. */
    private BrowseRow rowOf(MenuContext ctx) {
        return ctx.entry(BrowseRow.class);
    }

    /**
     * Resolve one browse level into its mixed, fully-rendered tiles: the sub-categories of {@code categoryId}
     * sorted by their slot, then the warps at that level, reproducing the old view's ordering. With no categories
     * defined this is just the warps, so the legacy flat list and the category root are one path.
     */
    private BrowseLevel level(PlayerRef viewer, List<Warp> warps, Optional<String> categoryId) {
        List<BrowseRow> rows = new ArrayList<>();
        for (WarpCategory category : subCategories(categoryId)) {
            rows.add(categoryRow(viewer, category));
        }
        for (Warp warp : levelWarps(warps, categoryId)) {
            rows.add(warpRow(viewer, warp));
        }
        return new BrowseLevel(warps, categoryId, rows);
    }

    /** The categories whose parent is {@code categoryId}, sorted by their display slot like the old view. */
    private List<WarpCategory> subCategories(Optional<String> categoryId) {
        return categoryRepository.all().stream()
                .filter(category -> category.parentCategoryId().equals(categoryId))
                .sorted(Comparator.comparingInt(WarpCategory::slot))
                .toList();
    }

    /** The warps that belong to {@code categoryId} (the uncategorised ones at the root). */
    private static List<Warp> levelWarps(List<Warp> warps, Optional<String> categoryId) {
        return warps.stream()
                .filter(warp -> warp.categoryId().equals(categoryId))
                .toList();
    }

    private BrowseRow categoryRow(PlayerRef viewer, WarpCategory category) {
        return new BrowseRow(
                Kind.CATEGORY,
                category.id(),
                WarpCategoryIcons.material(category).name(),
                resolve(viewer, WarpsMessageKey.WARP_MENU_CATEGORY_NAME, Map.of("category", category.displayName())),
                categoryLore(viewer, category));
    }

    private BrowseRow warpRow(PlayerRef viewer, Warp warp) {
        return new BrowseRow(
                Kind.WARP,
                warp.name().value(),
                warpMaterial(warp).name(),
                resolve(
                        viewer,
                        WarpsMessageKey.WARP_MENU_ENTRY_NAME,
                        Map.of("warp", warp.name().value())),
                warpLore(viewer, warp));
    }

    /** The category tile's lore: its configured display lore, or the catalog drill-in hint when it has none. */
    private String categoryLore(PlayerRef viewer, WarpCategory category) {
        if (!category.displayLore().isEmpty()) {
            return String.join("\n", category.displayLore());
        }
        return resolve(viewer, WarpsMessageKey.WARP_MENU_CATEGORY_LORE, Map.of());
    }

    /**
     * The warp tile's full lore as the catalog lines joined by {@code \n}, in the old view's order: the optional
     * cost, the optional required permission, the location, then the click-to-warp hint. The engine's multi-line
     * placeholder expansion splits this back into one lore component per line, each rendered as MiniMessage.
     */
    private String warpLore(PlayerRef viewer, Warp warp) {
        List<String> lines = new ArrayList<>();
        if (warp.hasCost()) {
            lines.add(resolve(
                    viewer,
                    WarpsMessageKey.WARP_MENU_LORE_COST,
                    Map.of("amount", warp.cost().amount().toPlainString())));
        }
        warp.requiredPermission()
                .ifPresent(permission -> lines.add(
                        resolve(viewer, WarpsMessageKey.WARP_MENU_LORE_PERMISSION, Map.of("permission", permission))));
        var at = warp.location();
        lines.add(resolve(
                viewer,
                WarpsMessageKey.WARP_MENU_LORE_LOCATION,
                Map.of(
                        "world", at.world().name(),
                        "x", Integer.toString(at.blockX()),
                        "y", Integer.toString(at.blockY()),
                        "z", Integer.toString(at.blockZ()))));
        lines.add(resolve(
                viewer,
                WarpsMessageKey.WARP_MENU_LORE_USABLE,
                Map.of("warp", warp.name().value())));
        return String.join("\n", lines);
    }

    /**
     * Click a tile: drill into a clicked category by re-opening the browse spec at that category's level, or warp
     * the viewer to a clicked warp through the same {@link UseWarp} use case the old view used. The clicked tile's
     * identity comes from the bound entry, never from re-reading the icon.
     */
    private void click(MenuActionContext ctx) {
        BrowseLevel level = ctx.subject(BrowseLevel.class);
        BrowseRow row = ctx.entry(BrowseRow.class);
        if (row.kind() == Kind.CATEGORY) {
            PlayerRef viewer = ctx.viewer();
            menus.open(viewer, SPEC_ID, level(viewer, level.warps(), Optional.of(row.id())));
            return;
        }
        warp(ctx.player(), ctx.viewer(), row.id());
    }

    /** Step up to the parent level: re-open the browse spec at the current level's parent category. */
    private void back(MenuActionContext ctx) {
        BrowseLevel level = ctx.subject(BrowseLevel.class);
        Optional<String> parentId =
                level.categoryId().flatMap(categoryRepository::find).flatMap(WarpCategory::parentCategoryId);
        PlayerRef viewer = ctx.viewer();
        menus.open(viewer, SPEC_ID, level(viewer, level.warps(), parentId));
    }

    /** Warp to the clicked warp and close the menu, on the viewer's entity thread (the hop moves the live player). */
    private void warp(Player player, PlayerRef viewer, String warpName) {
        scheduler.onEntity(viewer, () -> {
            useWarp.use(viewer, WarpName.of(warpName));
            player.closeInventory();
        });
    }

    /** The warp icon material: a valid per-warp override, else the single teleport-flavoured fallback. */
    private static Material warpMaterial(Warp warp) {
        if (warp.iconMaterial().isPresent()) {
            Material parsed = Material.matchMaterial(warp.iconMaterial().get());
            if (parsed != null && parsed != Material.AIR) {
                return parsed;
            }
        }
        return WARP_FALLBACK_ICON;
    }

    /** Resolve a catalog key to its MiniMessage source string in the viewer's locale, tokens filled. */
    private String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return messages.resolve(viewer, key, placeholders);
    }

    /**
     * The subject of an open browse level: the full warp snapshot, the current category id (empty at the root), and
     * the level's already-resolved mixed tiles. The list source reads {@link #entries()}; drilling and the back
     * action re-derive the next level from {@link #warps()} and {@link #categoryId()}, so no warp scan re-runs on a
     * click and the menu carries no port read once it opens.
     *
     * @param warps the full warp set the open was seeded with, carried so a drill can re-filter it per level
     * @param categoryId the category this level shows the children of, or empty at the root
     * @param entries the level's tiles (sub-categories then warps), each with its icon, name, and lore resolved
     */
    public record BrowseLevel(List<Warp> warps, Optional<String> categoryId, List<BrowseRow> entries) {

        public BrowseLevel {
            warps = List.copyOf(Objects.requireNonNull(warps, "warps"));
            Objects.requireNonNull(categoryId, "categoryId");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /**
     * One browse tile, fully resolved on the entity thread so the menu never reads a port again: its kind (a
     * drill-in category or a warp), the identity a click acts on (the category id or the warp name), the icon
     * material name, the rendered display name, and the full lore as {@code \n}-joined catalog lines.
     *
     * @param kind whether this tile drills into a category or warps to a warp
     * @param id the clicked identity, the category id to drill into, or the warp name to warp to
     * @param icon the icon material name
     * @param name the rendered display name in the viewer's locale
     * @param lore the full lore, the catalog lines joined by {@code \n} for the engine to expand
     */
    public record BrowseRow(Kind kind, String id, String icon, String name, String lore) {

        public BrowseRow {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(lore, "lore");
        }
    }
}
