package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.custommenus.adapter.MenuEditLocks;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService.EditOutcome;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridHandlers;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The slot-grid canvas of the {@code /menu editor}: a visual mirror of a menu's slots where an operator places, moves
 * and clears items before saving. It is a thin consumer of the engine's {@link Menus#openGrid} primitive: the canvas
 * is an engine {@code MenuHolder} window, every preview is rendered engine-side from a {@link MenuItemSpec}, and the
 * shift-click clear gates behind the engine's confirm, so no raw Bukkit inventory is ever built here and the grid
 * stays on the engine like every other editor surface.
 *
 * <p>An open clones the registered {@link MenuSpec} into a {@link MenuEditSession} held per viewer, so every edit
 * mutates a private working copy and the live menu changes only on Save (which validates through the P0 writer and
 * hot-reloads the single file, off the tick thread). The interactions mirror the OGUI editor: left-click a filled cell
 * opens its item editor, left-click an empty cell adds a default item and opens that same editor, right-click picks a
 * cell up and the next click drops it (moving, or swapping with the target), and shift-click clears a cell behind a
 * confirm. Selection is shown by glowing the picked-up cell.
 *
 * <p>The operator can also arrange the canvas by dragging or placing their own items straight onto it: the engine's
 * capture grid cancels every real transfer touching the top canvas and instead copies the held item's definition into
 * the slot ({@link #onCapture}), so a hotbar item stamps a cell without ever leaving the operator's inventory, no item
 * moves, so no dupe. A shift-click out of their inventory appends an item to the next free slot; their own inventory
 * otherwise stays freely interactive so they can pick an item onto the cursor to stamp with.
 */
@NullMarked
public final class MenuGridView {

    /** The material a freshly placed item starts as: a neutral stone the operator then re-skins in the item editor. */
    private static final String DEFAULT_MATERIAL = "STONE";

    /** The 36 viewer-inventory slots a bottom-inventory menu addresses on top of its {@code rows * 9} chest slots. */
    private static final int BOTTOM_SLOTS = 36;

    private static final Material EMPTY_ICON = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    private static final Material BLOCKER_ICON = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material BACK_ICON = Material.ARROW;
    private static final Material SAVE_ICON = Material.EMERALD;
    private static final Material PREVIEW_ICON = Material.SPYGLASS;
    private static final Material NAV_ICON = Material.ARROW;

    /** The control-row column the back button sits in (columns 0 and 8 are the engine's prev/next). */
    private static final int BACK_COLUMN = 3;

    /** The control-row column the live-preview button sits in, between back and save. */
    private static final int PREVIEW_COLUMN = 4;

    /** The control-row column the save button sits in. */
    private static final int SAVE_COLUMN = 5;

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final MenuEditorService service;
    private final MenuEditLocks locks;
    private final Function<String, Optional<MenuSpec>> specOf;
    private final BiConsumer<Player, String> onBack;
    private final MenuItemEditorView itemEditor;

    /** One editing session per viewer, holding the clone being edited and the picked-up slot (null when none). */
    private final Map<UUID, GridEditState> states = new ConcurrentHashMap<>();

    public MenuGridView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            MenuEditorService service,
            MenuEditLocks locks,
            Function<String, Optional<MenuSpec>> specOf,
            BiConsumer<Player, String> onBack,
            MenuItemEditorView itemEditor) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.service = Objects.requireNonNull(service, "service");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.specOf = Objects.requireNonNull(specOf, "specOf");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
        this.itemEditor = Objects.requireNonNull(itemEditor, "itemEditor");
    }

    /**
     * Open the slot grid for {@code menuId}: take the menu's edit lock, clone the registered spec into a fresh edit
     * session for this viewer, then show the canvas. A menu that is no longer registered simply tells the viewer so and
     * opens nothing; a menu another operator already has open in the editor is refused with a "being edited by …" line,
     * so two sessions can never edit, and then save over, the same menu.
     */
    public void open(Player player, PlayerRef viewer, String menuId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(menuId, "menuId");
        MenuSpec spec = specOf.apply(menuId).orElse(null);
        if (spec == null) {
            player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", menuId)));
            return;
        }
        Optional<String> lockedBy = locks.tryAcquire(menuId, viewer.uuid(), viewer.name());
        if (lockedBy.isPresent()) {
            player.sendMessage(
                    guiText.text(viewer, CustomMenusMessageKey.MENU_EDITOR_LOCKED, Map.of("player", lockedBy.get())));
            return;
        }
        GridEditState state = new GridEditState(menuId, MenuEditSession.from(spec));
        states.put(viewer.uuid(), state);
        showGrid(viewer, state);
    }

    /** The session a viewer is editing, exposed so a test can read the working copy without a live save. */
    Optional<MenuEditSession> editSession(UUID viewer) {
        return Optional.ofNullable(states.get(viewer)).map(state -> state.session);
    }

    /**
     * Reopen the grid for {@code viewer} over the session they were editing: the item editor's Back target. A no-op
     * when the viewer holds no open grid session (they closed it), so a late Back never opens a blank canvas.
     */
    public void reopenGrid(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        GridEditState state = states.get(viewer.uuid());
        if (state != null) {
            showGrid(viewer, state);
        }
    }

    /**
     * Capture the player's main-hand item into {@code slot} of the menu they are currently editing, the body of
     * {@code /menu captureitem <slot>}. It resolves the viewer's live grid session (the working copy they opened, which
     * survives closing the grid window to type the command), copies the held item with all its NBT into a {@code b64:}
     * token, and either updates the material of whatever item already occupies the slot or adds a fresh one there. It
     * then re-opens the grid so the change is visible. An empty hand, no active session, or a slot past the menu each
     * reply with the matching line and change nothing.
     */
    public void captureHeldItem(Player player, PlayerRef viewer, int slot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        GridEditState state = states.get(viewer.uuid());
        if (state == null) {
            player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_CAPTURE_NO_SESSION));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_CAPTURE_EMPTY_HAND));
            return;
        }
        int capacity = state.session.rows() * 9 + (state.session.bottomInventory() ? BOTTOM_SLOTS : 0);
        if (slot < 0 || slot >= capacity) {
            player.sendMessage(guiText.text(
                    viewer,
                    CustomMenusMessageKey.MENU_CAPTURE_BAD_SLOT,
                    Map.of("slot", Integer.toString(slot), "max", Integer.toString(capacity))));
            return;
        }
        applyCapture(state, slot, SerializedItems.encode(hand));
        player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_CAPTURE_CAPTURED, slot(slot)));
        showGrid(viewer, state);
    }

    /** Set the material of the item already at {@code slot}, or add a fresh captured item there when the slot is empty. */
    private void applyCapture(GridEditState state, int slot, String token) {
        String id = idAt(state.session, slot);
        if (id != null) {
            state.session.setMaterial(id, token);
        } else {
            state.session.addItem(freshId(state.session), capturedItem(slot, token));
        }
    }

    /** Build the grid spec from the current session and open it through the engine; reused on every re-open. */
    private void showGrid(PlayerRef viewer, GridEditState state) {
        GridSpec spec = new GridSpec(
                guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_TITLE, titlePlaceholders(state)),
                state.session.rows(),
                () -> contentOf(state),
                emptyIcon(viewer),
                ItemBuilder.of(BLOCKER_ICON).name(Component.empty()).build(),
                icon(viewer, NAV_ICON, GuiMessageKey.PAGE_PREVIOUS),
                icon(viewer, NAV_ICON, GuiMessageKey.PAGE_NEXT),
                controls(viewer, state));
        GridHandlers handlers = new GridHandlers(
                (view, player, menuSlot, filled, kind) -> onSlot(viewer, state, view, player, menuSlot, filled, kind),
                (view, player, menuSlot, item) -> onCapture(state, view, menuSlot, item));
        menus.openGrid(viewer, spec, handlers);
    }

    private Map<String, String> titlePlaceholders(GridEditState state) {
        return Map.of("name", state.menuId, "rows", Integer.toString(state.session.rows()));
    }

    /** The control-row buttons: back to the overview, a live preview of the working copy, and save over the P0 path. */
    private List<GridSpec.Control> controls(PlayerRef viewer, GridEditState state) {
        return List.of(
                new GridSpec.Control(
                        BACK_COLUMN,
                        icon(viewer, BACK_ICON, CustomMenusMessageKey.MENU_GRID_BACK),
                        player -> onBack.accept(player, state.menuId)),
                new GridSpec.Control(
                        PREVIEW_COLUMN,
                        icon(viewer, PREVIEW_ICON, CustomMenusMessageKey.MENU_GRID_PREVIEW),
                        player -> preview(viewer, state)),
                new GridSpec.Control(
                        SAVE_COLUMN,
                        icon(viewer, SAVE_ICON, CustomMenusMessageKey.MENU_GRID_SAVE),
                        player -> saveGrid(player, viewer, state)));
    }

    /**
     * Open the working copy as a live preview through the engine. The real renderer paints {@code session.toSpec()}
     * exactly as a player would see it, without touching disk, so the operator can look over their unsaved edits.
     * Closing the preview steps back to this grid (the engine's close hook), and the working copy is unchanged, so no
     * edit is lost by looking.
     */
    private void preview(PlayerRef viewer, GridEditState state) {
        menus.openPreview(viewer, state.session.toSpec(), () -> reopenGrid(viewer));
    }

    /**
     * The engine content map for the current draw: each occupied menu slot mapped to its item spec (the first item
     * wins a shared slot), with the picked-up slot's preview glowed so the selection is visible on the canvas.
     */
    private Map<Integer, MenuItemSpec> contentOf(GridEditState state) {
        Map<Integer, MenuItemSpec> content = new LinkedHashMap<>();
        for (Map.Entry<String, MenuItemSpec> entry : state.session.items().entrySet()) {
            for (int slot : entry.getValue().slots().slots()) {
                content.putIfAbsent(slot, entry.getValue());
            }
        }
        if (state.selected != null && content.containsKey(state.selected)) {
            content.put(state.selected, withGlow(content.get(state.selected)));
        }
        return content;
    }

    /**
     * Route a content-cell click made with an empty cursor: the editor gestures. A pending pick-up takes priority
     * the next click drops it onto the clicked cell (moving, or swapping with whatever is there). Otherwise a
     * shift-click clears a filled cell (behind a confirm), a right-click picks a filled cell up, a left-click on a
     * filled cell opens its item editor, and a left-click on an empty cell adds a default item and opens that editor.
     * Dropping one of the operator's own items onto a cell is a separate gesture the engine routes to
     * {@link #onCapture}; a selected hotbar item is no longer captured by an empty-cursor click.
     */
    private void onSlot(
            PlayerRef viewer,
            GridEditState state,
            GridView view,
            Player player,
            int menuSlot,
            boolean filled,
            ClickKind kind) {
        if (state.selected != null) {
            drop(viewer, state, view, player, menuSlot);
            return;
        }
        if (kind == ClickKind.SHIFT_LEFT || kind == ClickKind.SHIFT_RIGHT) {
            if (filled) {
                promptClear(viewer, state, player, menuSlot);
            }
            return;
        }
        if (kind == ClickKind.RIGHT) {
            if (filled) {
                pickUp(viewer, state, view, player, menuSlot);
            }
            return;
        }
        if (filled) {
            openSlotEditor(viewer, state, player, menuSlot);
        } else {
            addDefault(viewer, state, view, player, menuSlot);
        }
    }

    /**
     * Copy a dragged / placed item's full definition into {@code menuSlot} of the working copy, the capture seam the
     * engine calls when the operator drops one of their own items onto the canvas (a cursor place, a drag across cells,
     * or a shift-click out of their inventory). The item is encoded to a {@code b64:} token (all its NBT) and either
     * re-skins whatever already occupies the slot or is added as a fresh item there, then the canvas re-renders. It
     * sends no chat line: the repaint is the feedback, so stamping a whole row stays quiet. The operator's real item is
     * never moved, only its definition is copied, so no duplicate can be produced.
     */
    private void onCapture(GridEditState state, GridView view, int menuSlot, ItemStack item) {
        if (item.getType().isAir()) {
            return;
        }
        applyCapture(state, menuSlot, SerializedItems.encode(item));
        view.reRender();
    }

    /** Pick up the item at {@code menuSlot}, glowing it and waiting for the next click to place it. */
    private void pickUp(PlayerRef viewer, GridEditState state, GridView view, Player player, int menuSlot) {
        state.selected = menuSlot;
        player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_SELECTED, slot(menuSlot)));
        view.reRender();
    }

    /** Drop the picked-up item onto {@code target}: move it there, swapping with whatever already sits at the target. */
    private void drop(PlayerRef viewer, GridEditState state, GridView view, Player player, int target) {
        int from = state.selected == null ? target : state.selected;
        state.selected = null;
        if (from != target) {
            moveOrSwap(state, from, target);
            player.sendMessage(guiText.text(
                    viewer,
                    CustomMenusMessageKey.MENU_GRID_MOVED,
                    Map.of("from", Integer.toString(from), "to", Integer.toString(target))));
        }
        view.reRender();
    }

    /** Relocate the item at {@code from} to {@code target}, moving any item already at {@code target} back to {@code from}. */
    private void moveOrSwap(GridEditState state, int from, int target) {
        String fromId = idAt(state.session, from);
        if (fromId == null) {
            return;
        }
        String targetId = idAt(state.session, target);
        state.session.moveItem(fromId, new SlotSet(List.of(target)));
        if (targetId != null && !targetId.equals(fromId)) {
            state.session.moveItem(targetId, new SlotSet(List.of(from)));
        }
    }

    /** Add a default item at {@code menuSlot}, then open its item editor so it can be dressed up straight away. */
    private void addDefault(PlayerRef viewer, GridEditState state, GridView view, Player player, int menuSlot) {
        String id = freshId(state.session);
        state.session.addItem(id, defaultItem(menuSlot));
        player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_ADDED, slot(menuSlot)));
        view.reRender();
        openSlotEditor(viewer, state, player, menuSlot);
    }

    /**
     * Clear the item at {@code menuSlot} behind the engine confirm. On confirm the item is removed from the session
     * and the grid re-opens (the confirm window replaced it), so the cleared cell shows its empty placeholder again.
     */
    private void promptClear(PlayerRef viewer, GridEditState state, Player player, int menuSlot) {
        String id = idAt(state.session, menuSlot);
        if (id == null) {
            return;
        }
        menus.confirm(
                viewer,
                guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_CLEAR_CONFIRM, slot(menuSlot)),
                () -> confirmClear(viewer, state, player, id, menuSlot),
                () -> showGrid(viewer, state));
    }

    /** The confirm's yes branch: drop the item from the working copy, tell the viewer, and re-open the grid. */
    private void confirmClear(PlayerRef viewer, GridEditState state, Player player, String id, int menuSlot) {
        state.session.removeItem(id);
        player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_CLEARED, slot(menuSlot)));
        showGrid(viewer, state);
    }

    /**
     * Open the item property editor for whatever occupies {@code menuSlot}. The routing seam a filled cell (and a
     * freshly added one) lands on. The editor mutates this viewer's working copy in place and its Back reopens the
     * grid, so an edit shows on the canvas the moment the operator returns. A cell that holds nothing is a no-op.
     */
    private void openSlotEditor(PlayerRef viewer, GridEditState state, Player player, int menuSlot) {
        String id = idAt(state.session, menuSlot);
        if (id != null) {
            itemEditor.open(player, viewer, state.session, id);
        }
    }

    /** Save the edited session over the P0 write path off the tick thread, then report the outcome to the viewer. */
    private void saveGrid(Player player, PlayerRef viewer, GridEditState state) {
        scheduler.async(() -> {
            EditOutcome outcome = service.saveSession(state.menuId, state.session);
            scheduler.onEntity(
                    viewer,
                    () -> player.sendMessage(
                            guiText.text(viewer, saveKey(outcome), Map.of("name", state.menuId, "missing", ""))));
        });
    }

    private static MessageKey saveKey(EditOutcome outcome) {
        return switch (outcome) {
            case SAVED -> CustomMenusMessageKey.MENU_SAVED;
            case INVALID_REFS -> CustomMenusMessageKey.MENU_SAVE_INVALID;
            default -> CustomMenusMessageKey.MENU_SAVE_FAILED;
        };
    }

    /** The id of the item occupying {@code slot} (the first found), or null when the slot holds nothing. */
    private static @Nullable String idAt(MenuEditSession session, int slot) {
        for (Map.Entry<String, MenuItemSpec> entry : session.items().entrySet()) {
            if (entry.getValue().slots().slots().contains(slot)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** A fresh {@code item-<n>} id not already used in the session, so a new placement never overwrites an item. */
    private static String freshId(MenuEditSession session) {
        int n = 1;
        while (session.item("item-" + n).isPresent()) {
            n++;
        }
        return "item-" + n;
    }

    /** A minimal valid item at {@code menuSlot}: a plain stone with no name, lore, decor or actions: a P3 canvas. */
    private static MenuItemSpec defaultItem(int menuSlot) {
        return new MenuItemSpec(
                new SlotSet(List.of(menuSlot)),
                0,
                DEFAULT_MATERIAL,
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.<Ref>of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    /** A minimal item at {@code menuSlot} carrying {@code material}: a captured {@code b64:} token or any token. */
    private static MenuItemSpec capturedItem(int menuSlot, String material) {
        return defaultItem(menuSlot).withMaterial(material);
    }

    /** A copy of {@code item} with its enchant glow on: how the picked-up cell is shown as selected on the canvas. */
    private static MenuItemSpec withGlow(MenuItemSpec item) {
        return item.withDecor(item.decor().withGlow(true));
    }

    /** A control / placeholder icon of {@code material} named from the viewer's catalog, carrying no inline literal. */
    private ItemStack icon(PlayerRef viewer, Material material, MessageKey name) {
        return ItemBuilder.of(material).name(guiText.text(viewer, name)).build();
    }

    /**
     * The empty-cell placeholder: a one-word name plus the "click to add" hint on a separate lore line. The hint lives
     * in lore, not the name, a display name can't hold a line break, so folding it in rendered as a stray glyph box.
     */
    private ItemStack emptyIcon(PlayerRef viewer) {
        return ItemBuilder.of(EMPTY_ICON)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_EMPTY),
                        guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_EMPTY_LORE)))
                .build();
    }

    private static Map<String, String> slot(int menuSlot) {
        return Map.of("slot", Integer.toString(menuSlot));
    }

    /** The per-viewer editing state: the menu being edited, its working copy, and the picked-up slot (null when none). */
    private static final class GridEditState {
        private final String menuId;
        private final MenuEditSession session;
        private @Nullable Integer selected;

        private GridEditState(String menuId, MenuEditSession session) {
            this.menuId = menuId;
            this.session = session;
        }
    }
}
