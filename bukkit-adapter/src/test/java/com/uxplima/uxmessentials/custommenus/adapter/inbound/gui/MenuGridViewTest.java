package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditLocks;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuEditLockListener;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /menu editor} slot-grid canvas: the grid opens as a {@link MenuHolder} window
 * showing each item at its slot, a subtle placeholder at every empty slot, and a bottom control bar; a left-click on
 * an empty slot adds a default item, a right-then-left click moves an item, and a shift-click clears one behind the
 * engine confirm. Save writes the edited working copy through the P0 path and hot-reloads it. Everything is an engine
 * window, so the grid never touches a raw Bukkit inventory.
 */
class MenuGridViewTest {

    private static final int BACK_SLOT = 12; // control row (9) + BACK_COLUMN 3
    private static final int PREVIEW_SLOT = 13; // control row (9) + PREVIEW_COLUMN 4
    private static final int SAVE_SLOT = 14; // control row (9) + SAVE_COLUMN 5
    private static final int CONFIRM_YES_SLOT = 11; // ConfirmRenderer.YES_SLOT

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;
    private CustomMenuLoader loader;
    private final List<String> names = new ArrayList<>();
    private MenuEditLocks editLocks;
    private MenuGridView view;

    @TempDir
    Path menusDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", ctx -> {});
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);

        loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, menus, NOOP);
        writeMenu("alpha", 1, "x { slot = 0, material = STONE, click { left = [\"close\"] } }");
        writeMenu("big", 6, "x { slot = 50, material = STONE, click { left = [\"close\"] } }");
        names.addAll(loader.loadFrom(menusDir).loadedNames());

        MenuSpecPersistence persistence = new MenuSpecPersistence(new MenuSpecWriter(), bindings, NOOP);
        MenuEditorService service = new MenuEditorService(
                menusDir,
                persistence,
                menus::registeredSpec,
                name -> Optional.empty(),
                this::reloadOne,
                this::forget,
                NOOP);
        // The grid opens the item property editor for a clicked (or freshly added) cell; the item editor's Back reopens
        // the grid, so it is built with a Back hook that reads the grid through a holder set once the grid exists.
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        AtomicReference<MenuGridView> gridRef = new AtomicReference<>();
        AtomicReference<MenuItemEditorView> itemEditorRef = new AtomicReference<>();
        MenuRefListEditor refListEditor =
                new MenuRefListEditor(guiText, scheduler, textInput, "menu.action-editor.arg");
        MenuClickActionsView clickActions = new MenuClickActionsView(guiText, refListEditor, bindings::schema);
        MenuRequirementsView requirements = new MenuRequirementsView(
                menus,
                guiText,
                scheduler,
                new GuiLayouts(menusDir, NOOP),
                refListEditor,
                bindings::schema,
                (p, v) -> Objects.requireNonNull(itemEditorRef.get()).reopen(p, v));
        MenuItemEditorView itemEditor = new MenuItemEditorView(
                menus,
                guiText,
                scheduler,
                new KeyMessages(),
                textInput,
                new GuiLayouts(menusDir, NOOP),
                (p, v) -> Objects.requireNonNull(gridRef.get()).reopenGrid(v),
                clickActions,
                requirements);
        itemEditorRef.set(itemEditor);
        editLocks = new MenuEditLocks();
        view = new MenuGridView(
                menus, guiText, scheduler, service, editLocks, menus::registeredSpec, (p, id) -> {}, itemEditor);
        gridRef.set(view);
        server.getPluginManager().registerEvents(new MenuEditLockListener(editLocks), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void gridShowsItemsPlaceholdersAndTheControlBar() {
        view.open(player, viewer, "alpha");

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getSize()).isEqualTo(18); // one content row + one control row
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.STONE); // the item painted at its slot
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.LIGHT_GRAY_STAINED_GLASS_PANE); // empty placeholder
        assertThat(inv.getItem(BACK_SLOT).getType()).isEqualTo(Material.ARROW); // control bar: back
        assertThat(inv.getItem(SAVE_SLOT).getType()).isEqualTo(Material.EMERALD); // control bar: save
    }

    @Test
    void leftClickingAnEmptySlotAddsADefaultItem() {
        view.open(player, viewer, "alpha");

        fireClick(1, ClickType.LEFT);

        MenuEditSession session = editSession();
        assertThat(itemAt(session, 1)).isPresent(); // a default item now occupies slot 1
        assertThat(session.items()).hasSize(2); // the seeded item plus the new one
    }

    @Test
    void rightThenLeftClickMovesTheItemToTheTargetSlot() {
        view.open(player, viewer, "alpha");

        fireClick(0, ClickType.RIGHT); // pick up the item at slot 0
        fireClick(2, ClickType.LEFT); // drop it on slot 2

        MenuEditSession session = editSession();
        assertThat(itemAt(session, 2)).contains("x"); // the item moved to slot 2
        assertThat(itemAt(session, 0)).isEmpty(); // and left slot 0
    }

    @Test
    void shiftClickClearsTheSlotBehindAConfirm() {
        view.open(player, viewer, "alpha");

        fireClick(0, ClickType.SHIFT_LEFT); // opens the confirm, does not clear yet
        assertThat(itemAt(editSession(), 0)).contains("x");

        fireClick(CONFIRM_YES_SLOT, ClickType.LEFT); // confirm the clear
        assertThat(editSession().items()).isEmpty(); // the item is gone from the working copy
    }

    @Test
    void savingWritesTheEditedSessionAndReloadsIt() {
        view.open(player, viewer, "alpha");
        fireClick(0, ClickType.RIGHT); // pick up the item at slot 0
        fireClick(2, ClickType.LEFT); // drop it on slot 2. The grid stays open

        fireClick(SAVE_SLOT, ClickType.LEFT);

        MenuItemSpec moved = menus.registeredSpec("alpha").orElseThrow().items().values().stream()
                .filter(item -> item.slots().slots().contains(2))
                .findFirst()
                .orElse(null);
        assertThat(moved).isNotNull(); // the reloaded menu carries the item moved in the grid
        assertThat(menus.registeredSpec("alpha").orElseThrow().items()).hasSize(1);
    }

    @Test
    void aSixRowMenuPaginatesAcrossTwoPages() {
        view.open(player, viewer, "big");

        Inventory page0 = player.getOpenInventory().getTopInventory();
        assertThat(page0.getSize()).isEqualTo(54);
        assertThat(page0.getItem(53).getType()).isEqualTo(Material.ARROW); // next button on the control row

        fireClick(53, ClickType.LEFT); // flip to page 1

        Inventory page1 = player.getOpenInventory().getTopInventory();
        assertThat(page1.getItem(5).getType()).isEqualTo(Material.STONE); // menu slot 50 lands at canvas slot 5
    }

    // --- item capture via /menu captureitem ---

    @Test
    void leftClickingAnEmptyCellNoLongerCapturesTheMainHandItem() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        view.open(player, viewer, "alpha");

        fireClick(1, ClickType.LEFT); // an empty cell, an item in hand but an empty cursor

        // The empty-cursor left-click now always adds a plain default item and opens its editor; a selected hotbar
        // item is no longer captured this way: cursor placement and /menu captureitem are the capture paths.
        String id = itemAt(editSession(), 1).orElseThrow();
        assertThat(editSession().item(id).orElseThrow().material()).isEqualTo("STONE");
    }

    @Test
    void captureHeldItemUpdatesTheMaterialOfAFilledSlot() {
        view.open(player, viewer, "alpha");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        view.captureHeldItem(player, viewer, 0); // slot 0 holds the seeded item "x"

        assertThat(editSession().item("x").orElseThrow().material()).startsWith("b64:");
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(18); // grid re-opened
    }

    @Test
    void captureHeldItemIntoAnEmptySlotCreatesAnItem() {
        view.open(player, viewer, "alpha");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        view.captureHeldItem(player, viewer, 3); // an empty slot

        assertThat(itemAt(editSession(), 3)).isPresent();
        assertThat(editSession().items()).hasSize(2); // the seeded item plus the captured one
    }

    @Test
    void captureHeldItemWithAnEmptyHandChangesNothing() {
        view.open(player, viewer, "alpha");
        player.getInventory().setItemInMainHand(null);

        view.captureHeldItem(player, viewer, 0);

        assertThat(editSession().item("x").orElseThrow().material()).isEqualTo("STONE"); // untouched
    }

    @Test
    void captureHeldItemWithNoActiveSessionIsSafe() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        // No grid opened, so the viewer has no edit session.
        view.captureHeldItem(player, viewer, 0);

        assertThat(view.editSession(viewer.uuid())).isEmpty();
    }

    // --- P6: live preview ---

    @Test
    void previewOpensTheWorkingCopyThroughTheEngine() {
        view.open(player, viewer, "alpha");

        fireClick(PREVIEW_SLOT, ClickType.LEFT);

        Inventory preview = player.getOpenInventory().getTopInventory();
        assertThat(preview.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) preview.getHolder()).specId()).startsWith("preview:");
        assertThat(preview.getSize()).isEqualTo(9); // the real 1-row menu, as a player sees it
        assertThat(preview.getItem(0).getType()).isEqualTo(Material.STONE);
    }

    // The "close the preview → back to the grid" reopen defers to the viewer's next tick (opening a window inside an
    // InventoryCloseEvent is a Paper gotcha, so the reopen funnels through the scheduler). Under the synchronous test
    // scheduler that deferral collapses into the close and is clobbered by MockBukkit resetting the view, so the reopen
    // is covered where it is observable: MenusGridTest asserts the engine fires the close hook the grid wires to
    // reopenGrid, and previewOpensTheWorkingCopyThroughTheEngine asserts the preview opens.

    // --- P6: edit lock ---

    @Test
    void aSecondViewerCannotOpenAMenuAnotherIsEditing() {
        view.open(player, viewer, "alpha"); // Alice acquires the lock

        PlayerMock bob = server.addPlayer("Bob");
        PlayerRef bobRef = new PlayerRef(bob.getUniqueId(), bob.getName());
        view.open(bob, bobRef, "alpha");

        assertThat(view.editSession(bob.getUniqueId())).isEmpty(); // Bob was refused, no session opened
        assertThat(editLocks.holds("alpha", viewer.uuid())).isTrue();
    }

    @Test
    void theLockReleasesOnQuitSoAnotherViewerCanOpen() {
        view.open(player, viewer, "alpha"); // Alice acquires

        server.getPluginManager()
                .callEvent(new PlayerQuitEvent(
                        player,
                        net.kyori.adventure.text.Component.text("bye"),
                        PlayerQuitEvent.QuitReason.DISCONNECTED));

        PlayerMock bob = server.addPlayer("Bob");
        PlayerRef bobRef = new PlayerRef(bob.getUniqueId(), bob.getName());
        view.open(bob, bobRef, "alpha");

        assertThat(view.editSession(bob.getUniqueId())).isPresent(); // the lock freed, Bob may edit now
        assertThat(editLocks.holds("alpha", bob.getUniqueId())).isTrue();
    }

    // --- drag-from-inventory placement (the capture grid) ---

    @Test
    void placingACursorItemOnAFilledCellReskinsItInPlaceWithoutDuplicating() {
        view.open(player, viewer, "alpha"); // slot 0 holds the seeded item "x"

        InventoryClickEvent event = firePlace(0, new ItemStack(Material.DIAMOND_SWORD), InventoryAction.PLACE_ALL);

        assertThat(editSession().item("x").orElseThrow().material()).startsWith("b64:"); // re-skinned
        assertThat(editSession().items()).hasSize(1); // a copy into the model, never a new (duplicate) item
        assertThat(event.isCancelled()).isTrue(); // the real cursor item never moves
    }

    @Test
    void placingACursorItemOnAnEmptyCellAddsACapturedItem() {
        view.open(player, viewer, "alpha");

        InventoryClickEvent event = firePlace(3, new ItemStack(Material.DIAMOND_SWORD), InventoryAction.PLACE_ALL);

        String id = itemAt(editSession(), 3).orElseThrow();
        assertThat(editSession().item(id).orElseThrow().material()).startsWith("b64:");
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(18); // stamped in place, no editor
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void shiftClickingInventoryItemsAppendsThemToConsecutiveEmptySlots() {
        view.open(player, viewer, "alpha"); // slot 0 filled, slots 1..8 empty
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.GOLDEN_APPLE));

        InventoryClickEvent first = fireBottomClick(0, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        InventoryClickEvent second = fireBottomClick(1, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(itemAt(editSession(), 1)).isPresent(); // appended to the first empty menu slot
        assertThat(itemAt(editSession(), 2)).isPresent(); // then the next
        assertThat(first.isCancelled()).isTrue();
        assertThat(second.isCancelled()).isTrue();
    }

    @Test
    void anOrdinaryPickupInTheBottomInventoryIsNotCancelled() {
        view.open(player, viewer, "alpha");
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));

        InventoryClickEvent event = fireBottomClick(0, InventoryAction.PICKUP_ALL);

        assertThat(event.isCancelled()).isFalse(); // the operator keeps free control of their own items
    }

    @Test
    void aDoubleClickInTheBottomInventoryIsCancelled() {
        view.open(player, viewer, "alpha");
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));

        InventoryClickEvent event = fireBottomClick(0, InventoryAction.COLLECT_TO_CURSOR);

        assertThat(event.isCancelled()).isTrue(); // gathering top display icons onto the cursor is swallowed
    }

    @Test
    void draggingAcrossTwoContentCellsCapturesBothWithoutDuplicating() {
        view.open(player, viewer, "alpha");

        InventoryDragEvent event = fireDrag(new ItemStack(Material.DIAMOND_SWORD), 1, 2);

        assertThat(event.isCancelled()).isTrue();
        String one = itemAt(editSession(), 1).orElseThrow();
        String two = itemAt(editSession(), 2).orElseThrow();
        assertThat(editSession().item(one).orElseThrow().material()).startsWith("b64:");
        assertThat(editSession().item(two).orElseThrow().material()).startsWith("b64:");
    }

    @Test
    void aDragEntirelyInTheBottomInventoryIsNotCancelled() {
        view.open(player, viewer, "alpha");
        int topSize = player.getOpenInventory().getTopInventory().getSize();

        InventoryDragEvent event = fireDrag(new ItemStack(Material.DIAMOND_SWORD), topSize, topSize + 1);

        assertThat(event.isCancelled()).isFalse();
    }

    // --- helpers ---

    private MenuEditSession editSession() {
        return view.editSession(viewer.uuid()).orElseThrow();
    }

    private static Optional<String> itemAt(MenuEditSession session, int slot) {
        return session.items().entrySet().stream()
                .filter(entry -> entry.getValue().slots().slots().contains(slot))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private CustomMenuLoader.SingleLoad reloadOne(String name) {
        CustomMenuLoader.SingleLoad result = loader.loadSingle(menusDir, name);
        if (result.found() && result.loaded() > 0 && !names.contains(name)) {
            names.add(name);
        }
        return result;
    }

    private void forget(String name) {
        menus.unregisterSpec(name);
        names.remove(name);
    }

    private void writeMenu(String name, int rows, String item) {
        String hocon = "rows = " + rows + "\nitems { " + item + " }\n";
        try {
            Files.writeString(menusDir.resolve(name + ".conf"), hocon);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to seed menu " + name, failure);
        }
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView openView = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                openView, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Fire a place/swap on a top {@code rawSlot} with {@code cursor} held, mirroring a cursor drop onto a cell. */
    private InventoryClickEvent firePlace(int rawSlot, ItemStack cursor, InventoryAction action) {
        InventoryView openView = player.getOpenInventory();
        openView.setCursor(cursor);
        InventoryClickEvent event =
                new InventoryClickEvent(openView, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** Fire a click on the {@code bottomSlot}-th slot of the viewer's own inventory (raw slot past the top window). */
    private InventoryClickEvent fireBottomClick(int bottomSlot, InventoryAction action) {
        InventoryView openView = player.getOpenInventory();
        int rawSlot = openView.getTopInventory().getSize() + bottomSlot;
        ClickType type = action == InventoryAction.MOVE_TO_OTHER_INVENTORY ? ClickType.SHIFT_LEFT : ClickType.LEFT;
        InventoryClickEvent event =
                new InventoryClickEvent(openView, InventoryType.SlotType.CONTAINER, rawSlot, type, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** Fire a drag of {@code cursor} across the given raw slots and return the (possibly cancelled) event. */
    private InventoryDragEvent fireDrag(ItemStack cursor, int... rawSlots) {
        InventoryView openView = player.getOpenInventory();
        Map<Integer, ItemStack> added = new LinkedHashMap<>();
        for (int rawSlot : rawSlots) {
            added.put(rawSlot, cursor.clone());
        }
        InventoryDragEvent event = new InventoryDragEvent(openView, null, cursor, false, added);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
