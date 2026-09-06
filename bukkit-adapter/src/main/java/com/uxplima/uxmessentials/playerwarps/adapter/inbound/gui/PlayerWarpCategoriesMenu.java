package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SponsorConfig;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpBrowse;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.NullMarked;

/**
 * Registers and opens the player-warps landing ({@code pwarp-categories}) that a bare {@code /pwarp} opens. It is a
 * hub, not a warp list: four quick-entry buttons each open the paged {@link PlayerWarpBrowseMenu} with a preset filter
 *. The public browse, the viewer's own warps ({@code owner=<uuid>}), their favourites ({@code favouritesOf=<uuid>}),
 * and the top-rated view, and a button per defined category drills into the browse filtered to that category
 * ({@code category=<id>}). The landing never materialises the warp table; every entry hands off to the browse, whose
 * one bounded page query does the work.
 *
 * <p>The category buttons are the {@code playerwarps:categories} list source: a bounded snapshot of the shared
 * {@code warp_categories} set (player-warps share the categories the warps context defines) taken off the tick thread
 * when the menu opens and carried on the engine {@link Subject}. The snapshot is defensive, a read failure or an empty
 * set simply draws no category buttons rather than aborting the open. The sponsor slots render the currently-active
 * sponsors ({@code sponsored_until > now}, ordered by slot) as a second bounded snapshot; each drills into the
 * sponsored warp's {@code pwarp-view}. When the sponsor sub-group is off the snapshot is empty and the slots stay blank.
 */
@NullMarked
public final class PlayerWarpCategoriesMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "pwarp-categories";

    /** The category list-source id the spec's grid binds and this menu registers. */
    public static final String CATEGORY_SOURCE = "playerwarps:categories";

    /** The sponsor list-source id the reserved slots bind and this menu registers. */
    public static final String SPONSOR_SOURCE = "playerwarps:sponsors";

    /** Disk-first then bundled, mirroring the sibling player-warps menus, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/playerwarps/gui/pwarp-categories.conf";

    /** The icon a category with no configured display material falls back to. */
    private static final Material FALLBACK_ICON = Material.CHEST;

    /** The icon a sponsored warp with no configured (or an unresolvable rich) icon token falls back to. */
    private static final Material SPONSOR_FALLBACK_ICON = Material.DIAMOND;

    /** The reserved sponsor slots on the landing, so the snapshot never reads more sponsors than can be drawn. */
    private static final int SPONSOR_SLOTS = 3;

    /**
     * The category set shipped out of the box, so the hub reads as a full, useful panel on a fresh server rather than
     * an empty grid. Each is a browse filter tag with a localised name and an icon; a viewer's click filters the browse
     * to that tag exactly as an operator-defined category does. They are the fallback only: the moment the operator
     * defines any warp_categories, those replace the whole default set. Names resolve per viewer through the catalog.
     */
    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("building", PlayerwarpsMessageKey.PWARP_GUI_CATEGORIES_CAT_BUILDING, "OAK_PLANKS", 0),
            new DefaultCategory("shop", PlayerwarpsMessageKey.PWARP_GUI_CATEGORIES_CAT_SHOP, "EMERALD", 1),
            new DefaultCategory("pvp", PlayerwarpsMessageKey.PWARP_GUI_CATEGORIES_CAT_PVP, "IRON_SWORD", 2),
            new DefaultCategory("farm", PlayerwarpsMessageKey.PWARP_GUI_CATEGORIES_CAT_FARM, "WHEAT", 3),
            new DefaultCategory("redstone", PlayerwarpsMessageKey.PWARP_GUI_CATEGORIES_CAT_REDSTONE, "REDSTONE", 4),
            new DefaultCategory("misc", PlayerwarpsMessageKey.PWARP_GUI_CATEGORIES_CAT_MISC, "CHEST", 5));

    private final Menus menus;
    private final Scheduler scheduler;
    private final WarpCategoryRepository categories;
    private final Messages messages;
    private final PlayerWarpBrowse browse;
    private final BrowseOpener browseOpener;
    private final BiConsumer<PlayerRef, PlayerWarpName> openView;
    private final boolean sponsorEnabled;

    public PlayerWarpCategoriesMenu(
            Menus menus,
            Scheduler scheduler,
            WarpCategoryRepository categories,
            Messages messages,
            PlayerWarpBrowse browse,
            BrowseOpener browseOpener,
            BiConsumer<PlayerRef, PlayerWarpName> openView,
            SponsorConfig sponsorConfig) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.categories = Objects.requireNonNull(categories, "categories");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.browse = Objects.requireNonNull(browse, "browse");
        this.browseOpener = Objects.requireNonNull(browseOpener, "browseOpener");
        this.openView = Objects.requireNonNull(openView, "openView");
        this.sponsorEnabled =
                Objects.requireNonNull(sponsorConfig, "sponsorConfig").enabled();
    }

    /** One shipped default category: a browse-filter id, its localised name key, an icon material, and a sort slot. */
    private record DefaultCategory(String id, PlayerwarpsMessageKey nameKey, String material, int slot) {}

    /** Register the category and sponsor sources, the entry placeholders, the actions, and the spec. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(CATEGORY_SOURCE, ctx -> ctx.subject(Subject.class).categories());
        bindings.list(SPONSOR_SOURCE, ctx -> ctx.subject(Subject.class).sponsors());
        bindings.placeholder("pwarp_category_icon", this::icon);
        bindings.placeholder(
                "pwarp_category_name", ctx -> ctx.entry(WarpCategory.class).displayName());
        bindings.placeholder("pwarp_sponsor_icon", this::sponsorIcon);
        bindings.placeholder("pwarp_sponsor_name", ctx -> sponsorName(ctx.entry(WarpCard.class)));
        bindings.placeholder(
                "pwarp_sponsor_owner", ctx -> ctx.entry(WarpCard.class).ownerName());
        bindings.action("playerwarps:browse-all", ctx -> browseOpener.open(ctx.player(), ctx.viewer(), Map.of()));
        bindings.action("playerwarps:browse-mine", ctx -> openBrowse(ctx, "owner"));
        bindings.action("playerwarps:browse-favourites", ctx -> openBrowse(ctx, "favouritesOf"));
        bindings.action(
                "playerwarps:browse-top",
                ctx -> browseOpener.open(ctx.player(), ctx.viewer(), Map.of("sort", "rating")));
        bindings.action("playerwarps:category-open", this::openCategory);
        bindings.action("playerwarps:sponsor-open", this::openSponsor);
        // Opens this hub from the browse's categories control, so the landing stays reachable now that a bare /pwarp
        // opens the warp grid directly.
        bindings.action("playerwarps:open-categories", ctx -> open(ctx.player(), ctx.viewer()));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the landing for {@code viewer}. The bounded category and sponsor sets are snapshotted off the tick thread,
     * then the window is painted on the viewer's entity thread. The live {@code player} is kept only for call-site
     * symmetry with the browse opener the command drives; the engine resolves the live player from the viewer to render.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            Subject subject = new Subject(snapshotCategories(viewer), snapshotSponsors());
            scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, subject));
        });
    }

    /** Open the browse pre-filtered to the viewer under {@code key} (owner or favourites), from a quick-entry click. */
    private void openBrowse(MenuActionContext ctx, String key) {
        browseOpener.open(
                ctx.player(), ctx.viewer(), Map.of(key, ctx.viewer().uuid().toString()));
    }

    /** Drill into the browse filtered to the clicked category. */
    private void openCategory(MenuActionContext ctx) {
        WarpCategory category = ctx.entry(WarpCategory.class);
        browseOpener.open(ctx.player(), ctx.viewer(), Map.of("category", category.id()));
    }

    /** Drill into the clicked sponsor's detail panel, the same {@code pwarp-view} a browse tile opens. */
    private void openSponsor(MenuActionContext ctx) {
        WarpCard sponsor = ctx.entry(WarpCard.class);
        openView.accept(ctx.viewer(), PlayerWarpName.of(sponsor.name()));
    }

    /** The category cell's icon token: the category's configured display material, else the fallback. */
    private String icon(MenuContext ctx) {
        return ctx.entry(WarpCategory.class).displayMaterial().orElse(FALLBACK_ICON.name());
    }

    /** The sponsor cell's icon token: the warp's own icon, else the sponsor fallback. */
    private String sponsorIcon(MenuContext ctx) {
        String token = ctx.entry(WarpCard.class).icon();
        return token != null && !token.isBlank() ? token : SPONSOR_FALLBACK_ICON.name();
    }

    /** The sponsored warp's display name, falling back to its unique name when it sets none. */
    private static String sponsorName(WarpCard card) {
        String display = card.displayName();
        return display != null && !display.isBlank() ? display : card.name();
    }

    /**
     * The active sponsors for the reserved slots, a small bounded read (at most {@link #SPONSOR_SLOTS}) off the tick
     * thread; empty when the sponsor sub-group is off or the read faults, so the slots simply stay blank rather than
     * aborting the landing.
     */
    private List<WarpCard> snapshotSponsors() {
        if (!sponsorEnabled) {
            return List.of();
        }
        try {
            return browse.activeSponsors(SPONSOR_SLOTS);
        } catch (RuntimeException failure) {
            return List.of();
        }
    }

    /**
     * The bounded category snapshot for the list source, ordered by the operator's configured slot then id for a stable
     * layout. Read off the tick thread at open. The operator's defined categories win; when none are defined (a fresh
     * server) or the read faults, it falls back to the shipped {@link #DEFAULT_CATEGORIES} so the hub is always a full,
     * useful panel rather than an empty grid.
     */
    private List<WarpCategory> snapshotCategories(PlayerRef viewer) {
        try {
            List<WarpCategory> defined = categories.all().stream()
                    .sorted(Comparator.comparingInt(WarpCategory::slot).thenComparing(WarpCategory::id))
                    .toList();
            return defined.isEmpty() ? defaultCategories(viewer) : defined;
        } catch (RuntimeException failure) {
            return defaultCategories(viewer);
        }
    }

    /** The shipped default categories as {@link WarpCategory} tiles, each name resolved in the viewer's locale. */
    private List<WarpCategory> defaultCategories(PlayerRef viewer) {
        return DEFAULT_CATEGORIES.stream()
                .map(def -> new WarpCategory(
                        def.id(),
                        messages.resolve(viewer, def.nameKey(), Map.of()),
                        Optional.of(def.material()),
                        List.of(),
                        def.slot(),
                        Optional.empty()))
                .toList();
    }

    /**
     * Opens the paged browse with a preset filter, on the viewer's entity thread. The landing's quick entries and each
     * category button drive this; production binds it to {@link PlayerWarpBrowseMenu#open(Player, PlayerRef, Map)}.
     */
    @FunctionalInterface
    public interface BrowseOpener {
        void open(Player player, PlayerRef viewer, Map<String, String> filters);
    }

    /**
     * The subject of an open landing: the snapshotted category set and the active-sponsor set the two list sources
     * read. Both are resolved at open and read only by the render-time list sources, so the menu touches no per-viewer
     * mutable state.
     *
     * @param categories the bounded category snapshot taken off the tick thread at open (empty when none or unreadable)
     * @param sponsors the bounded active-sponsor snapshot (empty when the sponsor sub-group is off or unreadable)
     */
    public record Subject(List<WarpCategory> categories, List<WarpCard> sponsors) {

        public Subject {
            categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
            sponsors = List.copyOf(Objects.requireNonNull(sponsors, "sponsors"));
        }
    }
}
