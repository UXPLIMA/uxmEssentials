package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.homes.adapter.outbound.SafeLocationGuard;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the {@code /home} slot grid with the menu engine and opens it. One cell per addressable home slot the
 * owner could hold, from the first slot up to their resolved {@link HomeQuota} ceiling: a filled cell shows the home's
 * icon and opens the {@link HomeActionMenu} on click, an empty-but-addressable cell shows the empty bed and creates a
 * home there at the player's position. The two collapse into one {@link HomeCell} list template over the
 * {@code home:slots} source, the {@code home_icon} / {@code home_cell_name} / {@code home_cell_lore} placeholders
 * branch on whether the bound cell carries a home, and the page indicator fills from the engine's {@code %page%} /
 * {@code %max_page%}.
 *
 * <p>The slot list is built off the viewer's region thread because it reads homes and resolves the quota; it touches no
 * Bukkit API. The create flow replicates the old view exactly: the click reads the player's live position, hops to that
 * column's region for the block-safety and claim checks, then persists off-thread through the same
 * {@link CreateHomeAtSlot} the rest of homes uses, and reopens the grid. The action menu the filled cell opens is
 * reached through an injected seam so the grid does not name it directly.
 */
@NullMarked
public final class HomeListMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "home-list";

    private static final String SPEC_RESOURCE = "modules/homes/gui/home-list.conf";
    private static final String BYPASS_UNSAFE_PERMISSION = "uxmessentials.home.bypass.unsafe";

    private final Menus menus;
    private final Messages messages;
    private final Notifier notifier;
    private final Permissions permissions;
    private final Scheduler scheduler;
    private final ListHomes listHomes;
    private final HomeQuota quota;
    private final CreateHomeAtSlot createHome;
    private final SafeLocationGuard safeGuard;
    private final ClaimService claimService;
    private final HomeListLayout layout;
    private final int unlimitedMax;
    private final DateTimeFormatter dateFormat;
    private final OpenAction openAction;

    public HomeListMenu(
            Menus menus,
            Messages messages,
            Notifier notifier,
            Permissions permissions,
            Scheduler scheduler,
            ListHomes listHomes,
            HomeQuota quota,
            CreateHomeAtSlot createHome,
            SafeLocationGuard safeGuard,
            ClaimService claimService,
            HomeListLayout layout,
            int unlimitedMax,
            DateTimeFormatter dateFormat,
            OpenAction openAction) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.listHomes = Objects.requireNonNull(listHomes, "listHomes");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.createHome = Objects.requireNonNull(createHome, "createHome");
        this.safeGuard = Objects.requireNonNull(safeGuard, "safeGuard");
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.layout = Objects.requireNonNull(layout, "layout");
        if (unlimitedMax < 1) {
            throw new IllegalArgumentException("unlimitedMax must be >= 1, was " + unlimitedMax);
        }
        this.unlimitedMax = unlimitedMax;
        this.dateFormat = Objects.requireNonNull(dateFormat, "dateFormat");
        this.openAction = Objects.requireNonNull(openAction, "openAction");
    }

    /** Register the bindings the spec names and the spec itself; called once at homes wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("home:slots", this::slots);
        bindings.placeholder("home_icon", this::icon);
        bindings.placeholder("home_cell_name", this::cellName);
        bindings.placeholder("home_cell_lore", this::cellLore);
        bindings.action("homes:open-slot", this::openSlot);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 3, log));
    }

    /** Open the slot grid for {@code viewer}; the live player is resolved by the engine. */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, SPEC_ID, null);
    }

    /**
     * The slot list, built off the viewer's region thread (it reads homes and resolves the quota). Lists the owner's
     * homes by slot index, resolves the addressable-slot ceiling, and emits one {@link HomeCell} per index {@code
     * 0..maxSlots-1}: filled when a home occupies that index, empty otherwise. Every cell carries the owner's used
     * count and ceiling so an empty cell's lore can fill {@code {used}}/{@code {limit}} without re-reading the store.
     */
    private List<HomeCell> slots(MenuContext ctx) {
        PlayerRef viewer = ctx.viewer();
        Map<Integer, Home> bySlot = bySlot(viewer);
        int maxSlots = maxSlots(viewer);
        int used = bySlot.size();
        List<HomeCell> out = new ArrayList<>(maxSlots);
        for (int index = 0; index < maxSlots; index++) {
            out.add(new HomeCell(index, bySlot.get(index), used, maxSlots));
        }
        return out;
    }

    /** The cell's icon material name: the home's custom-or-fallback icon when filled, else the empty-slot icon. */
    private String icon(MenuContext ctx) {
        HomeCell cell = ctx.entry(HomeCell.class);
        Home home = cell.home();
        if (home == null) {
            return layout.emptyIcon().name();
        }
        return HomeIconResolver.resolve(home.icon(), layout.fallbackIcon()).name();
    }

    /** The cell's resolved name string: the home's label for a filled cell, the empty-slot name otherwise. */
    private String cellName(MenuContext ctx) {
        HomeCell cell = ctx.entry(HomeCell.class);
        Home home = cell.home();
        if (home == null) {
            return messages.resolve(ctx.viewer(), HomesMessageKey.HOME_MENU_EMPTY_NAME, Map.of());
        }
        return messages.resolve(ctx.viewer(), HomesMessageKey.HOME_MENU_DEFAULT_NAME, nameOf(home));
    }

    /** The cell's resolved lore string: the home's coordinate panel for a filled cell, the empty quota line otherwise. */
    private String cellLore(MenuContext ctx) {
        HomeCell cell = ctx.entry(HomeCell.class);
        Home home = cell.home();
        if (home == null) {
            return messages.resolve(
                    ctx.viewer(),
                    HomesMessageKey.HOME_MENU_EMPTY_LORE,
                    Map.of("used", Integer.toString(cell.used()), "limit", Integer.toString(cell.maxSlots())));
        }
        return messages.resolve(ctx.viewer(), HomesMessageKey.HOME_MENU_FILLED_LORE, filledLore(home));
    }

    /**
     * Left-click a cell. A filled cell opens the per-home action menu through the injected seam; an empty cell creates
     * a home at the player's current position, exactly as the old grid did.
     */
    private void openSlot(MenuActionContext ctx) {
        HomeCell cell = ctx.entry(HomeCell.class);
        Home home = cell.home();
        if (home != null) {
            openAction.open(ctx.viewer(), home);
            return;
        }
        create(ctx.player(), ctx.viewer(), cell.index());
    }

    /**
     * Create a home at {@code slotIndex}. The click runs on the viewer's entity thread, so the live position is read
     * here; the block-safety check reads the target column, which is only legal on that column's region thread, so we
     * hop there first, then persist async and reopen: the same threads and checks the old view used.
     */
    private void create(Player player, PlayerRef viewer, int slotIndex) {
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
        com.uxplima.uxmessentials.homes.domain.HomeSlot slot =
                com.uxplima.uxmessentials.homes.domain.HomeSlot.of(slotIndex);
        scheduler.onRegion(at, () -> {
            if (safeGuard.blockUnsafe() && !hasUnsafeBypass(viewer) && safeGuard.isUnsafe(at)) {
                rejectCreate(viewer, HomesMessageKey.HOME_UNSAFE_LOCATION);
                return;
            }
            ClaimDecision decision = claimService.canPlace(viewer, at);
            if (!decision.allowed()) {
                rejectCreate(viewer, claimMessageKey(decision));
                return;
            }
            scheduler.async(() -> {
                createHome.create(viewer, slot, at);
                scheduler.onEntity(viewer, () -> open(viewer));
            });
        });
    }

    private void rejectCreate(PlayerRef viewer, MessageKey key) {
        scheduler.onEntity(viewer, () -> {
            notifier.send(viewer, key);
            open(viewer);
        });
    }

    private boolean hasUnsafeBypass(PlayerRef viewer) {
        return permissions.has(viewer, BYPASS_UNSAFE_PERMISSION);
    }

    private static HomesMessageKey claimMessageKey(ClaimDecision decision) {
        return switch (decision) {
            case DENIED_FOREIGN -> HomesMessageKey.HOME_CLAIM_FOREIGN;
            case DENIED_REQUIRED -> HomesMessageKey.HOME_CLAIM_REQUIRED;
            case DENIED_TOO_CLOSE -> HomesMessageKey.HOME_CLAIM_TOO_CLOSE;
            case DENIED_ACCESS -> HomesMessageKey.HOME_CLAIM_ACCESS_DENIED;
            case ALLOWED -> throw new IllegalArgumentException("ALLOWED is not a denial");
        };
    }

    private Map<Integer, Home> bySlot(PlayerRef viewer) {
        Map<Integer, Home> bySlot = new HashMap<>();
        for (Home home : listHomes.homesOf(viewer)) {
            bySlot.put(home.slot().index(), home);
        }
        return bySlot;
    }

    private int maxSlots(PlayerRef viewer) {
        // Scope the quota to the viewer's world so a world-scoped limit node folds in, exactly as the old grid did.
        // getWorld() reads a cached reference and is safe off the entity thread; an offline viewer resolves unscoped.
        HomeLimit limit = quota.resolve(viewer, viewerWorld(viewer));
        return Math.min(limit.maxSlots(unlimitedMax), unlimitedMax);
    }

    @Nullable private WorldRef viewerWorld(PlayerRef viewer) {
        Player live = org.bukkit.Bukkit.getPlayer(viewer.uuid());
        return live == null ? null : BukkitRefs.toRef(live.getWorld());
    }

    private Map<String, String> nameOf(Home home) {
        String label = home.label()
                .map(HomeLabel::value)
                .orElseGet(() -> Integer.toString(home.slot().displayNumber()));
        return Map.of("home", label, "slot", Integer.toString(home.slot().displayNumber()));
    }

    private Map<String, String> filledLore(Home home) {
        return Map.of(
                "world", home.location().world().name(),
                "x", Integer.toString(home.location().blockX()),
                "y", Integer.toString(home.location().blockY()),
                "z", Integer.toString(home.location().blockZ()),
                "created", dateFormat.format(home.createdAt()));
    }

    /** The seam that opens the per-home action menu for a clicked filled cell, injected so the grid does not name it. */
    @FunctionalInterface
    public interface OpenAction {
        void open(PlayerRef viewer, Home home);
    }

    /**
     * One cell of the slot grid: an addressable slot index and the home that fills it, or {@code null} when the slot is
     * empty. The owner's current used count and addressable ceiling ride along so an empty cell's lore can render the
     * quota line without re-reading the store. The placeholders and the click action branch on whether {@link #home} is
     * present, so filled and empty share one list template.
     *
     * @param index the zero-based slot index this cell addresses
     * @param home the home occupying the slot, or {@code null} when the slot is empty
     * @param used how many homes the owner currently holds
     * @param maxSlots the addressable-slot ceiling at render time
     */
    public record HomeCell(int index, @Nullable Home home, int used, int maxSlots) {

        public HomeCell {
            if (index < 0) {
                throw new IllegalArgumentException("slot index must be >= 0: " + index);
            }
        }
    }
}
