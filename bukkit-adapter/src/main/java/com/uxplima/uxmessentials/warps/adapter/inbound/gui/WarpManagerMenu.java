package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the admin warp manager list (opened by {@code /warp editor} with no warp name, or the category manager's
 * back button) with the menu engine and opens it. One icon per stored warp across the top five rows, with a create, a
 * manage-categories, and a close button on the bottom row, and, like the original, no page arrows, so the grid is a
 * single page that truncates past what fits, exactly as the old view did.
 *
 * <p>The grid is the {@code warps:manager} list source. The repository read happens on the viewer's entity thread at
 * open and the rows are handed to the engine as the menu subject, so the engine renders off that snapshot without a
 * repository read of its own: mirroring {@link com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerMenu}. The
 * {@code warp_manager_icon} / {@code warp_manager_name} placeholders and the fixed catalog-key lore (category, location,
 * a spacer, the edit hint) read the bound row, reproducing the old fixed warp manager's icon slot for slot.
 *
 * <p>A left click on a warp runs {@code warps:manager-edit}, opening that warp's still-bespoke {@link WarpEditorView};
 * the create button runs {@code warps:manager-create}, the same name-prompt → create-at-position → editor flow the old
 * create button drove; the categories button runs {@code warps:manager-categories}, opening the still-bespoke
 * {@link WarpCategoryManagerMenu}; the close button shuts the window. The list adds no domain logic of its own.
 */
@NullMarked
public final class WarpManagerMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "warp-manager";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/warps/gui/warp-manager.conf";

    private final Menus menus;
    private final Scheduler scheduler;
    private final WarpRepository repository;
    private final Messages messages;
    private final WarpEditorView editorView;
    private final WarpCategoryManagerMenu categoryManagerView;
    private final BiConsumer<Player, PlayerRef> createPrompt;

    /**
     * @param createPrompt opens the name-prompt → create-at-position → editor flow for the viewer; reopening the
     *     manager on cancel is the caller's concern, exactly as the old manager's create button did
     */
    public WarpManagerMenu(
            Menus menus,
            Scheduler scheduler,
            WarpRepository repository,
            Messages messages,
            WarpEditorView editorView,
            WarpCategoryManagerMenu categoryManagerView,
            BiConsumer<Player, PlayerRef> createPrompt) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.categoryManagerView = Objects.requireNonNull(categoryManagerView, "categoryManagerView");
        this.createPrompt = Objects.requireNonNull(createPrompt, "createPrompt");
    }

    /** Register the bindings the spec names and the spec itself; called once at warps wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("warps:manager", ctx -> ctx.subject(WarpGrid.class).rows());
        bindings.placeholder("warp_manager_icon", ctx -> material(rowOf(ctx)).name());
        bindings.placeholder("warp_manager_name", ctx -> name(ctx.viewer(), rowOf(ctx)));
        bindings.placeholder("warp_manager_category", ctx -> category(ctx.viewer(), rowOf(ctx)));
        bindings.placeholder(
                "warp_manager_world", ctx -> rowOf(ctx).location().world().name());
        bindings.placeholder(
                "warp_manager_x", ctx -> Integer.toString(rowOf(ctx).location().blockX()));
        bindings.placeholder(
                "warp_manager_y", ctx -> Integer.toString(rowOf(ctx).location().blockY()));
        bindings.placeholder(
                "warp_manager_z", ctx -> Integer.toString(rowOf(ctx).location().blockZ()));
        bindings.action("warps:manager-edit", this::edit);
        bindings.action("warps:manager-create", this::create);
        bindings.action("warps:manager-categories", this::categories);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the warp manager for {@code viewer}. The repository read runs on the viewer's entity thread (where
     * {@code open} is called from), and the snapshot is handed to the engine as the subject so the engine renders
     * without a read of its own.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, snapshot()));
    }

    /** The current stored warps, read on the calling region thread, in repository order. */
    private WarpGrid snapshot() {
        return new WarpGrid(List.copyOf(repository.all()));
    }

    private Warp rowOf(MenuContext ctx) {
        return ctx.entry(Warp.class);
    }

    /** Left-click a warp icon: open that warp's bespoke editor on the viewer's entity thread. */
    private void edit(MenuActionContext ctx) {
        editorView.open(ctx.player(), ctx.viewer(), ctx.entry(Warp.class).name().value(), null);
    }

    /** Left-click the create button: run the name-prompt → create-at-position → editor flow the old button drove. */
    private void create(MenuActionContext ctx) {
        createPrompt.accept(ctx.player(), ctx.viewer());
    }

    /** Left-click the categories button: open the bespoke category manager, as the old slot-51 button did. */
    private void categories(MenuActionContext ctx) {
        categoryManagerView.open(ctx.player(), ctx.viewer());
    }

    /**
     * The warp icon's display name as a MiniMessage source the engine renders for the {@code %warp_manager_name%}
     * name line, reproducing the old view: the {@code warp.menu.entry.name} catalog line filled with the warp's name.
     * The engine renders this string through the same styled MiniMessage parser the old view used, so the rendered
     * label matches.
     */
    private String name(PlayerRef viewer, Warp warp) {
        return messages.resolve(
                viewer,
                WarpsMessageKey.WARP_MENU_ENTRY_NAME,
                Map.of("warp", warp.name().value()));
    }

    /** The warp's category id for the category lore line, or the localised "none" when it is uncategorised. */
    private String category(PlayerRef viewer, Warp warp) {
        return warp.categoryId()
                .orElseGet(() -> messages.resolve(viewer, WarpsMessageKey.WARP_EDITOR_VALUE_NONE, Map.of()));
    }

    /** The warp icon material, reproducing the old view: a valid per-warp override, else the ENDER_PEARL fallback. */
    private Material material(Warp warp) {
        if (warp.iconMaterial().isPresent()) {
            Material parsed = Material.matchMaterial(warp.iconMaterial().get());
            if (parsed != null && parsed != Material.AIR) {
                return parsed;
            }
        }
        return Material.ENDER_PEARL;
    }

    /**
     * The subject of an open warp manager: the stored warps, snapshotted on the region thread before the open so the
     * engine renders without a repository read. The list source and the placeholders read this, so the menu carries no
     * warp scan of its own.
     *
     * @param rows the stored warps in repository order
     */
    public record WarpGrid(List<Warp> rows) {

        public WarpGrid {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }
}
