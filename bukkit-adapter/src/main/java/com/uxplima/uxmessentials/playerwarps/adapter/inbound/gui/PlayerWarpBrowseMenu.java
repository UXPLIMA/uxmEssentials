package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SponsorConfig;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpBrowse;
import com.uxplima.uxmessentials.playerwarps.domain.Page;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;
import com.uxplima.uxmessentials.playerwarps.domain.WarpSort;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PinnedEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers and opens the paged player-warp browse ({@code pwarp-browse}), the first real consumer of the menu
 * engine's paged-list SPI. Where the {@code /pwarp} management list snapshots a whole owner-scoped set, this menu
 * binds a {@code playerwarps:browse} paged source: the engine holds the viewer's page, sort, and filter state and
 * asks the source for exactly one page per draw or flip, off the tick thread, so opening the browse costs one
 * bounded page query regardless of how many warps the server holds: it never materialises the table.
 *
 * <p>The source reads the viewer's {@link PageRequest}, maps its sort token and filters onto a {@link WarpQuery}
 * layered over the safe {@link WarpQuery#publicBrowse public browse} default (only active, public warps), asks the
 * {@link PlayerWarpBrowse} read model for that page, and maps each {@link WarpCard} to a lightweight {@link Tile}.
 * The mapping is pure data, no message resolution and no Bukkit call, so it is safe on the off-thread query. The
 * {@code %pwarp_browse_icon%} / {@code %pwarp_browse_name%} / {@code %pwarp_browse_lore%} placeholders then resolve
 * each tile's icon, display name, and catalog lore on the viewer's entity thread when the renderer draws the page.
 *
 * <p>The bottom bar drives the list through the shared list-control actions: a sort-cycle button advances the
 * offered sorts, a search button prompts for a name filter, and the scope buttons switch between the public browse,
 * the viewer's own warps, and their favourites. A tile's left click opens the {@code pwarp-view} detail panel for that
 * warp; a right click is the quick-teleport shortcut, straight through the same {@link UsePlayerWarp} use case
 * {@code /pwarp <name>} drives. The viewer's position is captured on the entity thread at open (for the distance sort);
 * the engine runs every page query off it.
 */
@NullMarked
public final class PlayerWarpBrowseMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "pwarp-browse";

    /** The paged-list source id the spec's grid binds and this menu registers. */
    public static final String LIST_SOURCE = "playerwarps:browse";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/playerwarps/gui/pwarp-browse.conf";

    /** The icon a card with no configured (or an unresolvable rich) token falls back to, matching the list menu. */
    private static final Material FALLBACK_ICON = Material.ENDER_PEARL;

    /** The default ordering when the spec offers no sort, or a sort token the read model does not recognise. */
    private static final WarpSort DEFAULT_SORT = WarpSort.RATING;

    /** The content slot the empty-state placeholder pins to, dead-centre of the five-row grid. */
    private static final int EMPTY_SLOT = 22;

    /**
     * The empty-state placeholder, pinned to the grid centre when a browse resolves to no warps at all so the grid
     * reads as a deliberate "no warps yet" panel rather than a bare gap. It is compared by reference: the one tile the
     * icon / name / lore / click paths special-case, and it never stands for a real warp.
     */
    private static final Tile EMPTY = new Tile(
            "",
            null,
            "",
            "",
            null,
            Material.BARRIER.name(),
            0L,
            0.0,
            0,
            0,
            BigDecimal.ZERO,
            "",
            WarpAccess.PUBLIC,
            false,
            EMPTY_SLOT);

    private final Menus menus;
    private final Scheduler scheduler;
    private final PlayerWarpBrowse browse;
    private final UsePlayerWarp usePlayerWarp;
    private final Messages messages;
    private final BiConsumer<PlayerRef, PlayerWarpName> openView;
    private final boolean sponsorEnabled;
    private final int sponsorSlots;

    public PlayerWarpBrowseMenu(
            Menus menus,
            Scheduler scheduler,
            PlayerWarpBrowse browse,
            UsePlayerWarp usePlayerWarp,
            Messages messages,
            BiConsumer<PlayerRef, PlayerWarpName> openView,
            SponsorConfig sponsorConfig) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.browse = Objects.requireNonNull(browse, "browse");
        this.usePlayerWarp = Objects.requireNonNull(usePlayerWarp, "usePlayerWarp");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.openView = Objects.requireNonNull(openView, "openView");
        Objects.requireNonNull(sponsorConfig, "sponsorConfig");
        this.sponsorEnabled = sponsorConfig.enabled();
        this.sponsorSlots = sponsorConfig.slots();
    }

    /** Register the paged source, the tile placeholders, the click action, and the spec; called once at wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.pagedList(LIST_SOURCE, this::page);
        bindings.placeholder("pwarp_browse_icon", ctx -> icon(tileOf(ctx)));
        bindings.placeholder("pwarp_browse_name", this::name);
        bindings.placeholder("pwarp_browse_lore", this::lore);
        bindings.action("playerwarps:browse-click", this::click);
        bindings.action("playerwarps:browse-teleport", this::teleport);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the browse for {@code viewer} at the public default, on their entity thread. */
    public void open(Player player, PlayerRef viewer) {
        open(player, viewer, Map.of());
    }

    /**
     * Open the browse for {@code viewer} with {@code presetFilters} pre-applied (the my-warps / favourites entry
     * points a later task drives), on their entity thread. The viewer's position is captured here, on the entity
     * thread the open is called from, so the off-thread distance sort has a stable location without a Bukkit read.
     */
    public void open(Player player, PlayerRef viewer, Map<String, String> presetFilters) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(presetFilters, "presetFilters");
        Position position = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        Subject subject = new Subject(Optional.of(position), Map.copyOf(presetFilters));
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, subject));
    }

    /**
     * The paged source: read the viewer's request, build one bounded {@link WarpQuery}, ask the read model for that
     * page, and map its cards to tiles. Runs off the viewer's entity thread on each draw and flip; it reads only the
     * immutable subject and request handed in and never snapshots the table, so its cost is the one page query.
     */
    private PagedResult<Tile> page(MenuContext ctx, PageRequest request) {
        Subject subject = ctx.subject(Subject.class);
        Map<String, String> filters = merge(subject.presetFilters(), request.filters());
        List<Tile> pinned = pinnedSponsors(request.size());
        if (pinned.isEmpty()) {
            WarpQuery query = query(
                    ctx.viewer().uuid(),
                    subject.viewerPosition(),
                    resolveSort(request.sort()),
                    request.page(),
                    pageSize(request.size()),
                    filters);
            Page<WarpCard> page = browse.page(query);
            List<Tile> tiles =
                    page.items().stream().map(PlayerWarpBrowseMenu::toTile).toList();
            if (tiles.isEmpty()) {
                // No warps match at all: pin the centred placeholder so the grid reads as a deliberate empty state
                // rather than a bare page. Reported total is zero, so the page bar shows one empty page.
                return new PagedResult<>(List.of(), 0L, List.of(EMPTY));
            }
            return PagedResult.of(tiles, page.totalCount());
        }
        // Pinned sponsors claim the top content slots on every page, so the flow only has the remaining slots to fill:
        // window the read by that effective size and report a total that makes the engine's pageCount, which divides
        // by the full grid size, page the flow correctly over the smaller window. The pinned warps are dropped from
        // the flow so a sponsor is never drawn twice.
        int effective = Math.max(1, request.size() - pinned.size());
        WarpQuery query = query(
                ctx.viewer().uuid(),
                subject.viewerPosition(),
                resolveSort(request.sort()),
                request.page(),
                pageSize(effective),
                filters);
        Page<WarpCard> page = browse.page(query);
        Set<String> pinnedNames = pinned.stream().map(Tile::name).collect(Collectors.toUnmodifiableSet());
        List<Tile> tiles = page.items().stream()
                .filter(card -> !pinnedNames.contains(card.name()))
                .map(PlayerWarpBrowseMenu::toTile)
                .toList();
        return new PagedResult<>(tiles, reportedTotal(page.totalCount(), effective, request.size()), pinned);
    }

    /**
     * The active sponsors as pinned tiles, ordered by their slot and capped to what the config allows and the page
     * can hold. A bounded off-tick read via the browse read model; empty when the sponsor sub-group is off, so a
     * disabled sub-group pins nothing.
     */
    private List<Tile> pinnedSponsors(int size) {
        if (!sponsorEnabled || sponsorSlots <= 0) {
            return List.of();
        }
        int limit = Math.min(sponsorSlots, size);
        List<WarpCard> sponsors = browse.activeSponsors(limit);
        List<Tile> pinned = new ArrayList<>();
        for (int i = 0; i < sponsors.size() && i < limit; i++) {
            pinned.add(toTile(sponsors.get(i), i));
        }
        return pinned;
    }

    /**
     * The total to report so the engine's {@code pageCount(size)}, which divides the reported total by the full grid
     * {@code size}: yields the number of pages a flow windowed by {@code effective} actually needs. Only the page-nav
     * bounding reads this total; the browse shows no count.
     */
    private static long reportedTotal(long realTotal, int effective, int size) {
        if (realTotal <= 0) {
            return 0L;
        }
        long pages = (realTotal + effective - 1) / effective;
        return pages * (long) size;
    }

    /**
     * The bounded query for one page, layered over the safe public browse: the scope button chooses owner-only or
     * favourites-only, and the search / category filters narrow further. Access stays PUBLIC and the active-only flag
     * stays set, so a private, suspended, or archived warp can never leak through the browse.
     *
     * <p>The scope button (in-menu) and the landing's preset filters are two ways in to the same owner / favourites
     * narrowing: the {@code scope=mine} / {@code scope=favourites} tokens the bottom-bar buttons set resolve to the
     * viewer, while the {@code owner=<uuid>} / {@code favouritesOf=<uuid>} keys the pwarp-categories landing pre-applies
     * carry an explicit subject. An explicit uuid wins; a malformed one is ignored so the browse widens rather than
     * throwing.
     */
    private static WarpQuery query(
            UUID viewer, Optional<Position> position, WarpSort sort, int page, int size, Map<String, String> filters) {
        String scope = filters.getOrDefault("scope", "");
        Optional<UUID> owner = presentUuid(filters.get("owner"))
                .or(() -> scope.equals("mine") ? Optional.of(viewer) : Optional.empty());
        Optional<UUID> favouritesOf = presentUuid(filters.get("favouritesOf"))
                .or(() -> scope.equals("favourites") ? Optional.of(viewer) : Optional.empty());
        return new WarpQuery(
                present(filters.get("category")),
                Optional.of(WarpAccess.PUBLIC),
                Optional.empty(),
                owner,
                present(filters.get("search")),
                favouritesOf,
                true,
                sort,
                page,
                size,
                viewer,
                position);
    }

    /** The tile the slot being rendered or clicked stands for. */
    private Tile tileOf(MenuContext ctx) {
        return ctx.entry(Tile.class);
    }

    /** The tile's icon token: the card's own icon resolved through the rich icon schemes, else the fallback. */
    private String icon(Tile tile) {
        String token = tile.icon();
        return token != null && !token.isBlank() ? token : FALLBACK_ICON.name();
    }

    /** The tile's rendered display name in the viewer's locale, the display name, or the warp name when unset. */
    private String name(MenuContext ctx) {
        Tile tile = tileOf(ctx);
        if (tile == EMPTY) {
            return resolve(ctx.viewer(), PlayerwarpsMessageKey.PWARP_GUI_BROWSE_EMPTY, Map.of());
        }
        return resolve(
                ctx.viewer(), PlayerwarpsMessageKey.PWARP_GUI_BROWSE_ENTRY_NAME, Map.of("warp", displayName(tile)));
    }

    /**
     * The tile's full lore as the catalog lines joined by {@code \n} in a fixed order, owner, world, optional server,
     * visits, rating, favourites, optional price, access, then the click hint. The engine's multi-line placeholder
     * expansion splits this back into one lore component per line, each rendered as MiniMessage.
     */
    private String lore(MenuContext ctx) {
        Tile tile = tileOf(ctx);
        PlayerRef viewer = ctx.viewer();
        if (tile == EMPTY) {
            return resolve(viewer, PlayerwarpsMessageKey.PWARP_GUI_BROWSE_EMPTY_LORE, Map.of());
        }
        List<String> lines = new ArrayList<>();
        lines.add(
                resolve(viewer, PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_OWNER, Map.of("owner", tile.ownerName())));
        lines.add(resolve(viewer, PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_WORLD, Map.of("world", tile.world())));
        if (tile.server() != null) {
            lines.add(resolve(
                    viewer, PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_SERVER, Map.of("server", tile.server())));
        }
        lines.add(resolve(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_VISITS,
                Map.of("visits", Long.toString(tile.visits()))));
        lines.add(resolve(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_RATING,
                Map.of("rating", rating(tile.ratingAvg()), "count", Integer.toString(tile.ratingCount()))));
        lines.add(resolve(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_FAVOURITES,
                Map.of("favourites", Integer.toString(tile.favourites()))));
        if (tile.price().signum() > 0) {
            lines.add(resolve(
                    viewer,
                    PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_PRICE,
                    Map.of("amount", tile.price().toPlainString(), "currency", tile.currency())));
        }
        lines.add(resolve(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_ACCESS,
                Map.of("access", tile.access().name().toLowerCase(Locale.ROOT))));
        if (tile.sponsored()) {
            lines.add(resolve(viewer, PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_SPONSORED, Map.of()));
        }
        lines.add(
                resolve(viewer, PlayerwarpsMessageKey.PWARP_GUI_BROWSE_LORE_CLICK, Map.of("warp", displayName(tile))));
        return String.join("\n", lines);
    }

    /**
     * Left-click a tile: open the {@code pwarp-view} detail panel for that warp, where the viewer can read its full
     * detail and choose to teleport, favourite, or rate it. The panel resolves the warp itself off the tick thread and
     * opens on the viewer's entity thread, so the click here only hands the clicked warp's name to the panel opener.
     */
    private void click(MenuActionContext ctx) {
        Tile tile = ctx.entry(Tile.class);
        if (tile == EMPTY) {
            return;
        }
        openView.accept(ctx.viewer(), PlayerWarpName.of(tile.name()));
    }

    /**
     * Right-click a tile: the quick-teleport shortcut, straight to the warp through the same {@link UsePlayerWarp} gate
     * the command and the detail panel drive, skipping the panel. The warp read runs off the tick thread; the window
     * closes on the viewer's entity thread this click already runs on.
     */
    private void teleport(MenuActionContext ctx) {
        Tile tile = ctx.entry(Tile.class);
        if (tile == EMPTY) {
            return;
        }
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = PlayerWarpName.of(tile.name());
        ctx.player().closeInventory();
        scheduler.async(() -> usePlayerWarp.useFor(viewer, name, Optional.empty()));
    }

    /** Map a read-model card to a flow tile (never pinned); pure data, safe on the query thread. */
    private static Tile toTile(WarpCard card) {
        return toTile(card, Tile.FLOWS);
    }

    /**
     * Map a read-model card to a tile pinned to {@code pinnedSlot} (or {@link Tile#FLOWS} to scroll with the flow).
     * Pure data, no message resolution and no Bukkit call, so it is safe on the off-thread page query.
     */
    private static Tile toTile(WarpCard card, int pinnedSlot) {
        return new Tile(
                card.name(),
                card.displayName(),
                card.ownerName(),
                card.world(),
                card.server(),
                card.icon(),
                card.visits(),
                card.ratingAvg(),
                card.ratingCount(),
                card.favourites(),
                card.price(),
                card.currency(),
                card.access(),
                card.sponsored(),
                pinnedSlot);
    }

    private static String displayName(Tile tile) {
        String display = tile.displayName();
        return display != null && !display.isBlank() ? display : tile.name();
    }

    /** The mean rating rounded to one decimal, as a plain data string (never a chat-format call). */
    private static String rating(double average) {
        return Double.toString(Math.round(average * 10.0) / 10.0);
    }

    /** Map a spec-offered sort token to a {@link WarpSort}; a blank or unknown token falls back to the default. */
    private static WarpSort resolveSort(String token) {
        if (token.isBlank()) {
            return DEFAULT_SORT;
        }
        try {
            return WarpSort.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return DEFAULT_SORT;
        }
    }

    /** Clamp the requested page size into the read model's accepted range, a guard against an oversized page. */
    private static int pageSize(int size) {
        return Math.min(Math.max(1, size), WarpQuery.MAX_PAGE_SIZE);
    }

    /** The preset and live filters overlaid, the live request winning; the common empty-preset case avoids a copy. */
    private static Map<String, String> merge(Map<String, String> presets, Map<String, String> live) {
        if (presets.isEmpty()) {
            return live;
        }
        Map<String, String> merged = new HashMap<>(presets);
        merged.putAll(live);
        return merged;
    }

    /** A present, non-blank filter value, or empty so an absent or cleared filter widens the result. */
    private static Optional<String> present(@Nullable String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** A present, well-formed uuid filter value, or empty so an absent, blank, or malformed one widens the result. */
    private static Optional<UUID> presentUuid(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return messages.resolve(viewer, key, placeholders);
    }

    /**
     * The subject of an open browse: the viewer's captured position (for the distance sort) and any preset filters an
     * entry point pre-applied. Both are resolved on the entity thread at open and read only off-thread by the paged
     * source, so the source touches no per-viewer mutable state and no Bukkit API.
     *
     * @param viewerPosition the viewer's location at open, for the distance ordering; empty when unavailable
     * @param presetFilters the filters an entry point pre-applied (empty for the bare public browse)
     */
    public record Subject(Optional<Position> viewerPosition, Map<String, String> presetFilters) {

        public Subject {
            Objects.requireNonNull(viewerPosition, "viewerPosition");
            presetFilters = Map.copyOf(Objects.requireNonNull(presetFilters, "presetFilters"));
        }
    }

    /**
     * One browse card flattened to exactly what a tile renders, mapped from a {@link WarpCard} on the query thread so
     * the render path resolves messages from plain fields without another read. The warp {@link #name} is the identity
     * a click teleports to.
     */
    public record Tile(
            String name,
            @Nullable String displayName,
            String ownerName,
            String world,
            @Nullable String server,
            @Nullable String icon,
            long visits,
            double ratingAvg,
            int ratingCount,
            int favourites,
            BigDecimal price,
            String currency,
            WarpAccess access,
            boolean sponsored,
            int pinnedSlot)
            implements PinnedEntry {

        /** The pinned-slot sentinel for a tile that flows through the scrolling content slots rather than a fixed one. */
        public static final int FLOWS = -1;

        public Tile {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(ownerName, "ownerName");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(access, "access");
        }

        /** The content slot a sponsored tile pins to, or {@link #FLOWS} for an ordinary tile that scrolls. */
        @Override
        public int pinnedSlot() {
            return pinnedSlot;
        }
    }
}
